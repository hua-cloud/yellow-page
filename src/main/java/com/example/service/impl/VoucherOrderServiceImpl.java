package com.example.service.impl;

import com.example.config.RabbitMQConfig;
import com.example.dto.Result;
import com.example.entity.VoucherOrder;
import com.example.mapper.VoucherOrderMapper;
import com.example.service.ISeckillVoucherService;
import com.example.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.utils.RedisIDWorker;
import com.example.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIDWorker redisIDWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    // @Resource
    // private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 定义一个阻塞队列，用来存放订单信息，所有的线程都会添加订单信息到这同一个阻塞队列中
    // private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    // 定义一个只包含一个线程的线程池(单线程执行器)，用其完成异步下单的操作
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    /** 在该类对象被初始化之后立刻执行此方法
     * @PostConstruct 添加了该注解的方法只会在该类对象初始化结束或依赖注入完成之后执行
     * 又因为当前类对象交给Spring IOC容器来创建，也就是说当前类的bean对象是单例的
     * 只会被创建一次，那么也就意味着该方法也只会执行一次，与多少个线程没有任何关系
     * 也就是无论有多少个线程并发执行，也只会创建一个独立的线程来完成异步下单操作
     */
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true){
                try {
                    // 1.获取队列中的订单信息
                    // VoucherOrder voucherOrder = orderTasks.take();
                    VoucherOrder voucherOrder= (VoucherOrder) rabbitTemplate.receiveAndConvert(RabbitMQConfig.QUEUE_NAME);
                    if (voucherOrder != null) {
                        // 2.创建订单
                        createVoucherOrder(voucherOrder);
                    } else {
                        // 当消息队列为空时，将当前线程暂时休眠
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException e) {
                    log.error("进行异步下单时出现异常", e);
                }
            }
        }
    }


    /**
     * 基于Redis实现的异步秒杀（新）
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        // 2.判断返回的结果是否为0
        int r = result.intValue();
        if (r != 0) {
            // 2.1.不为0，表示没有购买资格
            return Result.fail(r == 1? "库存不足！":"不能重复下单！");
        }
        // 2.2.为0表示有购买资格，把相关信息保存到阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.3.订单id
        long orderId = redisIDWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 2.4.用户id
        voucherOrder.setUserId(userId);
        // 2.5.优惠券id
        voucherOrder.setVoucherId(voucherId);
        // 2.6.放入到阻塞队列中
        // orderTasks.add(voucherOrder);
        // 2.6.更换为使用RabbitMQ来实现，此处即向消息队列中添加当前订单消息
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME,"work", voucherOrder);

        return Result.ok(orderId);
    }

    // TODO 还需要对方法进行优化
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 扣减库存
        seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        // 新增订单
        save(voucherOrder);
    }

    /**
     * 秒杀优惠券下单(旧)
     * 添加transactional注解将当前方法声明为事务
     * 以保证对两张表操作的整体的原子性
     * 使用乐观锁解决超卖问题
     * @param voucherId
     * @return
     */
    /*@Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始!");
        }
        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 已经结束
            return Result.fail("秒杀已经结束！");
        }
        // 已经结束
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }
        // 5.扣减库存,使用CAS方案实现乐观锁
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                // where id = ? and stock > 0
                .eq("voucher_id", voucherId).gt("stock",0)
                .update();

        if (!success) {
            // 扣减失败
            return Result.fail("库存不足！");
        }
        // 6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIDWorker.nextId("order");
        voucherOrder.setId(orderId);
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        // 插入订单信息
        save(voucherOrder);
        // 7.返回订单id
        return Result.ok(orderId);
    }*/
}
