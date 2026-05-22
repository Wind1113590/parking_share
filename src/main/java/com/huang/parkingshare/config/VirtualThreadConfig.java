package com.huang.parkingshare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class VirtualThreadConfig {

    /**
     * 使用虚拟线程的执行器（Spring Boot 3.2+ 推荐）
     */
    @Bean
    public Executor taskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
        // 或者使用 ThreadPoolTaskExecutor 配合虚拟线程工厂，但上面更简洁
    }
}