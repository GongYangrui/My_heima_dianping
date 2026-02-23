package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import io.lettuce.core.SslOptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * ClassName: SimpleRedisLock
 * Package: com.hmdp.utils
 * Description: 实现 Redis 分布锁
 *
 * @Author GYR
 * @Create 2026/2/23 18:03
 * @Version 1.0
 */
public class SimpleRedisLock implements ILock{
    private StringRedisTemplate stringRedisTemplate;
    private String key;
    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String key) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.key = key;
    }
    private static final String KEY_PREFIX = "lock:";
    private static final String UUID_PREFIX = UUID.randomUUID().toString(true);
    private static final DefaultRedisScript<Long> unlockScript;
    static {
        unlockScript = new DefaultRedisScript<>();
        unlockScript.setLocation(new ClassPathResource("unlock.lua"));
        unlockScript.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSeconds) {
        String lockKey = KEY_PREFIX + key;
        long id = Thread.currentThread().getId();
        String value = UUID_PREFIX + id; // 保证每一个线程存储的 key 值一定值独一无二的，从而在解锁的时候不会误删
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, value, timeoutSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(b);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.execute(
                unlockScript,
                Collections.singletonList(KEY_PREFIX + key),
                UUID_PREFIX + Thread.currentThread().getId());
    }
}
