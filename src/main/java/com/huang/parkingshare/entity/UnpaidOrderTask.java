package com.huang.parkingshare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("unpaid_order_task")
public class UnpaidOrderTask {
    @TableId(type = IdType.AUTO)
    Long id;
    Long orderId;
    Long userId;
    LocalDateTime createTime;
    LocalDateTime shouldCancelTime;
    Integer status;
    Integer retryCount;
    String lastError;
    LocalDateTime updateTime;

}


