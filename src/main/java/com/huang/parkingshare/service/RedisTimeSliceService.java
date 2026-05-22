package com.huang.parkingshare.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RedisTimeSliceService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String SLICE_KEY_PREFIX = "slice:slot:";

    /**
     * 初始化某车位某一天的所有时间片到 Redis Hash
     * @param slotId     车位ID
     * @param date       日期
     * @param minutes    该天的所有起始分钟列表
     */
    public void initTimeSlicesForDay(Long slotId, LocalDate date, List<Integer> minutes) {
        String key = SLICE_KEY_PREFIX + slotId + ":" + date.toString();
        // 使用 Hash 结构，field = startMinute, value = "0" (空闲)
        for (Integer minute : minutes) {
            redisTemplate.opsForHash().put(key, minute.toString(), "0");
        }
        // 设置过期时间（例如30天，避免无限堆积）
        redisTemplate.expire(key, 30, TimeUnit.DAYS);
    }
}