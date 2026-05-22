package com.huang.parkingshare.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ParkingSlotVO {
    private Long slotId;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal basePricePerHour;
    private BigDecimal totalPrice;    // 预计总价
    private Double distanceKm;        // 距离目的地的距离（公里）
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}