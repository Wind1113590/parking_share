package com.huang.parkingshare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("time_slice")
public class TimeSlice {
    private Long id; //批量插入手动生成雪花算法ID
    private Long slotId;
    private LocalDate sliceDate;
    private Integer startMinute;   // 从0点开始的分钟数，如 480 = 08:00
    private Integer endMinute;     // 495 = 08:15
    private Integer status;        // 0空闲 1锁定中 2已预约 3不可用
    private Long orderId;
}