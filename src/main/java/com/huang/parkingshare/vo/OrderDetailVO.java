package com.huang.parkingshare.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderDetailVO {
    private Long orderId;
    private Long slotId;
    private String address;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal totalAmount;
    private Integer status;
    private String statusDesc;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
    private LocalDateTime actualEndTime;   // 实际离场时间
    private BigDecimal actualAmount;       // 实际支付金额
}