package com.huang.parkingshare.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huang.parkingshare.common.Result;
import com.huang.parkingshare.dto.OrderCreateRequest;
import com.huang.parkingshare.entity.Order;
import com.huang.parkingshare.service.OrderService;
import com.huang.parkingshare.vo.OrderVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    public Result<Order> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        return Result.success(orderService.createOrder(request));
    }

    @PostMapping("/start/{orderId}")
    public Result<String> startUsing(@PathVariable Long orderId) {
        orderService.startUsing(orderId);
        return Result.success("入场成功");
    }

    @PostMapping("/complete/{orderId}")
    public Result<String> completeOrder(@PathVariable Long orderId) {
        orderService.completeOrder(orderId);
        return Result.success("离场成功，订单已完成");
    }

    @GetMapping("/list")
    public Result<Page<OrderVO>> listOrders(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        return Result.success(orderService.getUserOrders(pageNum, pageSize, status));
    }
}