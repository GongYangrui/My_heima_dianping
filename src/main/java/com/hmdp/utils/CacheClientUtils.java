package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jdk.vm.ci.meta.Local;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * ClassName: CacheClientUtils
 * Package: com.hmdp.utils
 * Description: 用于缓存使用的工具类
 *
 * @Author GYR
 * @Create 2026/2/22 20:25
 * @Version 1.0
 */
@Component
public class CacheClientUtils {
    private final StringRedisTemplate stringRedisTemplate;
    private final ExecutorService CACHE_REBUILD_EXECUTE = Executors.newFixedThreadPool(8);

    public CacheClientUtils(StringRedisTemplate stringRedisTemplate) { // Spring 自动进行依赖注入
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 向缓存中存入键值对，并且自动设置过期时间
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void setWithTTL(String key, Object value, Long time, TimeUnit timeUnit) {
        String jsonStr = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key, jsonStr, time, timeUnit);
    }

    /**
     * 向缓存中存入键值对，并且设置逻辑超时时间，用于缓存击穿时逻辑判断是否喜欢重写缓存
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 获取数据并且防止缓存穿透
     * @param keyPreFix
     * @param queryId
     * @param clazz
     * @param dbQueryFunction
     * @param time 缓存时间
     * @param timeUnit 时间单位
     * @return
     * @param <R> 返回数据类型
     * @param <ID> 查询的 ID 的数据类型
     */
    public <R, ID> R queryWithPassThrough(
            String keyPreFix,
            ID queryId,
            Class<R> clazz,
            Function<ID, R> dbQueryFunction,
            Long time,
            TimeUnit timeUnit
    ) {
        String key = keyPreFix + queryId;
        // 查询 Redis 缓存中的数据
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(jsonStr)) {
            // 如果存在，那么转换为返回对象之后直接返回
            R r = JSONUtil.toBean(jsonStr, clazz);
            return r;
        }
        // 如果是空字符串，那么代表就是我们之前为了应付缓存穿透设置的值，直接返回
        if (jsonStr != null) {
            return null;
        }
        // 如果直接都是一个空指针，说明需要设置 Redis 缓存，那么需要查询数据库
        R r = dbQueryFunction.apply(queryId);
        if (r == null) {
            // 数据库都查询不到数据，那么代表出现了缓存穿透，需要往 Redis 缓存空字符串
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 数据库查到对应的数据，缓存到 Redis 中
        setWithTTL(key, r, time, timeUnit);
        return r;
    }

    /**
     * 查询并且应对缓存击穿问题，利用逻辑超时来重建缓存 (热点 key 问题)
     * @param keyPreFix
     * @param queryId
     * @param clazz
     * @param dbQueryFunction
     * @param time
     * @param timeUnit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPreFix,
            ID queryId,
            Class<R> clazz,
            Function<ID, R> dbQueryFunction,
            Long time,
            TimeUnit timeUnit
    ) {
        String key = keyPreFix + queryId;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(jsonStr)) {
            return null;
        }
        // 如果命中，将 JSON 字符串转换为 Java 对象
        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, clazz);
        // 判断是否已经逻辑过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 如果还没有过期，就直接返回数据
            return r;
        }
        // 如果已经过期了，则需要进行缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + queryId;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            // 尝试获取互斥锁，如果成功获取，那么就进行缓存重建
            // doublecheck一下 Redis 缓存是否已经更新成功
            String freshJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(freshJson)) {
                RedisData freshRedisData = JSONUtil.toBean(freshJson, RedisData.class);
                LocalDateTime freshExpireTime = freshRedisData.getExpireTime();
                if (freshExpireTime != null && freshExpireTime.isAfter(LocalDateTime.now())) {
                    // 已经被更新为未过期，不需要重建
                    return JSONUtil.toBean((JSONObject) freshRedisData.getData(), clazz);
                }
            }
            CACHE_REBUILD_EXECUTE.submit(() -> {
                try {
                    R r1 = dbQueryFunction.apply(queryId);
                    setWithLogicalExpire(key, r1, time, timeUnit);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    unLock(lockKey);
                }
            });
        }
        // 如果获取互斥锁失败，就说明已经有线程在进行缓存重建，那么直接返回旧数据
        return r;
    }

    private boolean tryLock(String lockKey) {
        // 尝试上锁，如果已经上锁了，返回 true，没有被上锁返回 false
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10L, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    private void unLock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }

}
