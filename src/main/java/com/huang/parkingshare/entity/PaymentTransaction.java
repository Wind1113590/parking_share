package com.huang.parkingshare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_transaction")
public class PaymentTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String outTradeNo;
    private BigDecimal amount;
    private Integer status;
    private LocalDateTime createTime;

}
