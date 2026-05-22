package com.huang.parkingshare.controller;

import com.huang.parkingshare.dto.SearchParkingRequest;
import com.huang.parkingshare.service.ParkingSlotService;
import com.huang.parkingshare.vo.ParkingSlotVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private ParkingSlotService parkingSlotService;

    @PostMapping("/slots")
    public List<ParkingSlotVO> searchAvailableSlots(@Valid @RequestBody SearchParkingRequest request) {
        return parkingSlotService.searchAvailableSlots(request);
    }
}