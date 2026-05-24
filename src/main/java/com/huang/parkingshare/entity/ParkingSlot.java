package com.huang.parkingshare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("parking_slot")
public class ParkingSlot {
    @TableId(type = IdType.ASSIGN_ID)  // 雪花算法
    private Long id;
    private Long ownerId;
    private String address;
    private BigDecimal latitude;       // 注意数据库是 decimal(10,8)
    private BigDecimal longitude;
    private BigDecimal basePricePerHour;
    private Integer status;            // 1正常 0停用
    private LocalTime startTime;       // 每日可预约开始时间，如 08:00:00
    private LocalTime endTime;         // 每日可预约结束时间，如 20:00:00
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}