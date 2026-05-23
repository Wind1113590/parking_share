package com.huang.parkingshare.controller;

import com.huang.parkingshare.dto.OrderCreateRequest;
import com.huang.parkingshare.entity.Order;
import com.huang.parkingshare.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    public Order createOrder(@Valid @RequestBody OrderCreateRequest request) {
        return orderService.createOrder(request);
    }

    @PostMapping("/start/{orderId}")
    public String startUsing(@PathVariable Long orderId) {
        orderService.startUsing(orderId);
        return "入场成功";
    }

    @PostMapping("/complete/{orderId}")
    public String completeOrder(@PathVariable Long orderId) {
        orderService.completeOrder(orderId);
        return "离场成功，订单已完成";
    }
}