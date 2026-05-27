package com.huang.parkingshare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("`order`")
public class Order {
    @TableId(type = IdType.ASSIGN_ID)  // 雪花算法
    private Long id;
    private Long userId;
    private Long slotId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal totalAmount;
    private Integer status;            //'0待支付 1已支付 2使用中 3已完成 4已取消 5已退款'
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
    private LocalDateTime createTime;
    private LocalDateTime actualEndTime;
    private BigDecimal actualAmount;

}
