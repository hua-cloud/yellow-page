package com.example.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.dto.Result;
import com.example.entity.Shop;
import com.example.mapper.ShopMapper;
import com.example.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static com.example.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据商铺id查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryShopById(Long id) {
        // 解决缓存穿透的方法
        // Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿问题
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺信息不存在");
        }
        // 返回
        return Result.ok(shop);
    }

    /**
     * 将解决缓存击穿以及缓存穿透的代码封装到一个单独的方法中
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断命中的是否是空值""
        // shopJson == null 和 shopJson == "" 是两种不同的情况
        if (shopJson != null) {
            // 返回null
            return null;
        }
        // 3.实现缓存重建
        // 3.1.获取互斥锁
        String lockKey = "lock:shop:" + id;
        boolean isLock = tryLock(lockKey);
        Shop shop = null;
        try {
            // 3.2.判断是否获取成功
            if (!isLock) {
                // 3.4.失败则休眠重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 3.5.成功则查询数据库以及写缓存
            shop = getById(id);
            // 4.不存在返回null
            if (shop == null) {
                // 将空值也写入到redis中，来解决缓存穿透的问题
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                // 返回null
                return null;
            }
            // 5.数据库中存在则进行写缓存操作，采用超时剔除策略作为保底策略，设置键值对有效期30min
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 6.释放互斥锁
            unLock(lockKey);
        }
        // 7.返回
        return shop;
    }
    /**
     * 将解决缓存穿透的代码封装到一个单独的方法中
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断命中的是否是空值""
        // shopJson == null 和 shopJson == "" 是两种不同的情况
        if (shopJson != null) {
            // 返回null
            return null;
        }
        // 4.不存在，通过id查询数据库
        Shop shop = getById(id);
        // 5.数据库中也不存在，返回错误
        if (shop == null) {
            // 将空值也写入到redis中，来解决缓存穿透的问题
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            // 返回null
            return null;
        }
        // 6.数据库中存在写入到缓存中，即redis中,采用超时剔除策略作为保底策略，设置键值对有效期30min
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7.返回
        return shop;
    }

    // 用于解决缓存击穿问题的申请互斥锁方法
    private boolean tryLock(String key) {
        // 使用setnx(不存在才能插入)指令模拟申请锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 使用工具类中的方法来实现拆箱
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 根据商铺的id更新商铺信息
     * 使用 @Transactional 注解来声明一个方法或类需要进行事务管理。
     * 在方法或类上添加 @Transactional 注解后，Spring Boot 会在方法执行前开启一个事务，
     * 并在方法执行完成后根据方法执行的结果来提交或回滚事务。
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺的id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
