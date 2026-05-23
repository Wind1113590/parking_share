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
}