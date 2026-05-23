package com.huang.parkingshare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("owner_earning")
public class OwnerEarning {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private Long orderId;
    private BigDecimal amount;
    private Integer status;
    private LocalDate settleDate;
}
