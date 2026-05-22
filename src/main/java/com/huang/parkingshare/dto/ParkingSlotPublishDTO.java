package com.huang.parkingshare.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalTime;

@Data
public class ParkingSlotPublishDTO {
    private Long ownerId;               // 业主用户ID
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal basePricePerHour;
    private LocalTime startTime;        // 每日可预约开始时间，如 08:00
    private LocalTime endTime;          // 每日可预约结束时间，如 20:00
}