package com.huang.parkingshare.controller;

import com.huang.parkingshare.dto.ParkingSlotPublishRequest;
import com.huang.parkingshare.entity.ParkingSlot;
import com.huang.parkingshare.service.ParkingSlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parking-slot")
public class ParkingSlotController {

    @Autowired
    private ParkingSlotService parkingSlotService;

    @PostMapping("/publish")
    public ParkingSlot publish(@RequestBody ParkingSlotPublishRequest dto) {
        // 简单校验
        if (dto.getOwnerId() == null || dto.getAddress() == null) {
            throw new RuntimeException("参数不完整");
        }
        return parkingSlotService.publishSlot(dto);
    }
}