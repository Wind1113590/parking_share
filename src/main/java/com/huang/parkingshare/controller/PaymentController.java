package com.huang.parkingshare.controller;

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
    public String pay(@PathVariable Long orderId) {
        orderService.payOrder(orderId);
        return "支付成功";
    }
}