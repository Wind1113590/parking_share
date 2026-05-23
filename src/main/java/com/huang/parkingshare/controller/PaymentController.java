package com.huang.parkingshare.controller;

import com.huang.parkingshare.common.Result;
import com.huang.parkingshare.dto.PaymentRequest;
import com.huang.parkingshare.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {
    private final OrderService orderService;

    @PostMapping("/pay/{orderId}")
    public Result<String> pay(@PathVariable Long orderId) {
        orderService.payOrder(orderId);
        return Result.success("支付成功");
    }
}