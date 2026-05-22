package com.huang.parkingshare.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class SnowflakeIdWorker {

    // 起始时间戳 (2025-01-01 00:00:00)
    private final long twepoch = 1735660800000L;

    // 机器ID所占位数
    private final long workerIdBits = 5L;
    // 数据中心ID所占位数
    private final long datacenterIdBits = 5L;
    // 支持的最大机器ID (31)
    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
    // 支持的最大数据中心ID (31)
    private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
    // 序列号所占位数
    private final long sequenceBits = 12L;
    // 机器ID左移位数 (12)
    private final long workerIdShift = sequenceBits;
    // 数据中心ID左移位数 (12+5=17)
    private final long datacenterIdShift = sequenceBits + workerIdBits;
    // 时间戳左移位数 (12+5+5=22)
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
    // 序列号最大值 (4095)
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);

    // 机器ID (0~31)
    private long workerId;
    // 数据中心ID (0~31)
    private long datacenterId;
    // 序列号
    private long sequence = 0L;
    // 上次生成ID的时间戳
    private long lastTimestamp = -1L;

    // 从配置文件读取（可选，若未配置则默认0）
    @Value("${snowflake.worker-id:0}")
    private long configWorkerId;

    @Value("${snowflake.datacenter-id:0}")
    private long configDatacenterId;

    @PostConstruct
    public void init() {
        if (configWorkerId > maxWorkerId || configWorkerId < 0) {
            throw new IllegalArgumentException(String.format("workerId 不能大于 %d 或小于 0", maxWorkerId));
        }
        if (configDatacenterId > maxDatacenterId || configDatacenterId < 0) {
            throw new IllegalArgumentException(String.format("datacenterId 不能大于 %d 或小于 0", maxDatacenterId));
        }
        this.workerId = configWorkerId;
        this.datacenterId = configDatacenterId;
    }

    /**
     * 线程安全的生成下一个ID
     */
    public synchronized long nextId() {
        long timestamp = timeGen();

        // 时钟回拨处理
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                // 容忍5ms内的回拨，等待
                try {
                    wait(offset << 1);
                    timestamp = timeGen();
                    if (timestamp < lastTimestamp) {
                        throw new RuntimeException(String.format("时钟回拨超过容忍范围，拒绝生成ID: %d 毫秒", offset));
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException(String.format("时钟回拨过大，拒绝生成ID: %d 毫秒", offset));
            }
        }

        if (timestamp == lastTimestamp) {
            // 同一毫秒内，序列号递增
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                // 序列号溢出，等待下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，序列号重置
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 组合ID
        return ((timestamp - twepoch) << timestampLeftShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << workerIdShift)
                | sequence;
    }

    /**
     * 等待直到下一毫秒
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 获取当前毫秒数
     */
    private long timeGen() {
        return System.currentTimeMillis();
    }
}