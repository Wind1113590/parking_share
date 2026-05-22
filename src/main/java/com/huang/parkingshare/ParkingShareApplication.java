package com.huang.parkingshare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // 开启定时任务（业主结算等）
public class ParkingShareApplication {
    public static void main(String[] args) {
        SpringApplication.run(ParkingShareApplication.class, args);
    }
}