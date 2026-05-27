package com.huang.parkingshare.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OwnerEarningVO {
        private Long orderId;
        private BigDecimal amount;
        private Integer status;
        private String statusDesc;
        private LocalDate settleDate;
}
