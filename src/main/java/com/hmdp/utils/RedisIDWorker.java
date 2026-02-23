package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * ClassName: RedisIDWorker
 * Package: com.hmdp.utils
 * Description:
 *
 * @Author GYR
 * @Create 2026/2/23 10:19
 * @Version 1.0
 */
@Component
public class RedisIDWorker {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    public final int NUMBER_OF_ID = 32;
    /**
     * 利用 Redis 实现同一业务的全局唯一 ID
     * @param key 业务名称
     * @return 当前业务的全局唯一 ID
     */
    public long nextId(String key) {
        LocalDateTime now = LocalDateTime.now();
        //now.atZone(ZoneId.systemDefault())给当前时间带上时区信息
        //计算当前时间距离1970-01-01 00:00:00 UTC过去了多少秒
        long timestamp = now.atZone(ZoneId.systemDefault()).toEpochSecond();
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long increment = stringRedisTemplate.opsForValue().increment("icr:" + key + ":" + date); // 如果 Redis 中没有对应的 key，那么在一开始的时候会将初始值设置为 0.并且自增 1，最后得到 1
        return (timestamp << NUMBER_OF_ID) | increment;
    }
}
