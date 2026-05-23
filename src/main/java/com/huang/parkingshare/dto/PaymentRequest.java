package com.huang.parkingshare.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class PaymentRequest {
    @NotNull
    private Long orderId;
}