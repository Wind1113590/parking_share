package com.huang.parkingshare.controller;

import com.huang.parkingshare.dto.ParkingSlotPublishRequest;
import com.huang.parkingshare.entity.ParkingSlot;
import com.huang.parkingshare.service.ParkingSlotService;
import jakarta.validation.Valid;
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
    public ParkingSlot publish(@Valid @RequestBody ParkingSlotPublishRequest dto) {
        return parkingSlotService.publishSlot(dto);
    }
}