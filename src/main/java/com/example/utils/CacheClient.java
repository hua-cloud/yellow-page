package com.example.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.entity.RedisData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.example.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);

    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期的时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入到Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 直接将解决了缓存穿透问题后的查询方法封装到工具类中
     * 巧妙利用泛型来解决类型的问题
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallBack
     * @param time
     * @param unit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack, Long time, TimeUnit unit) {
        String key =  keyPrefix + id;
        // 1.从Redis种查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3.存在，则缓存命中直接返回
            return JSONUtil.toBean(json, type);
        }
        // 4.判断命中的是否为空值
        if (json != null) {
            // 表明是空值，返回null
            return null;
        }
        // 5.不存在，根据id查询数据库
        R r = dbFallBack.apply(id);
        // 6.不存在，返回null
        if (r == null) {
            // 进行写空值操作解决缓存穿透问题
            stringRedisTemplate.opsForValue().set(key,"", RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }

        // 7.存在，写缓存
        this.set(key, r, time, unit);

        return r;
    }

    // 初始化线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    /**
     * 将解决了缓存击穿问题后的查询方法封装到工具类中
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3.为空直接返回null
            return null;
        }
        // 4.命中缓存需要先把Json反序列化对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期直接返回商铺信息
            return r;
        }
        // 5.2.过期需要进行缓存重建
        // 6.缓存重建
        // 6.1.尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2.判断是否获取成功
        if (isLock) {
            // 6.3.成功则开启独立线程进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 缓存重建
                    // 查询数据库
                    R r1 = dbFallback.apply(id);
                    // 写缓存
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }
        // 6.4.无论是否获取到最终都会返回过期的商铺信息
        return r;
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
}
