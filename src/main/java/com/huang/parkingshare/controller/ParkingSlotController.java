package com.huang.parkingshare.controller;

import com.huang.parkingshare.common.Result;
import com.huang.parkingshare.dto.*;
import com.huang.parkingshare.entity.ParkingSlot;
import com.huang.parkingshare.service.ParkingSlotService;
import com.huang.parkingshare.vo.ParkingSlotDetailVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parking-slot")
public class ParkingSlotController {

    @Autowired
    private ParkingSlotService parkingSlotService;

    @PostMapping("/publish")
    public Result<ParkingSlot> publish(@Valid @RequestBody ParkingSlotPublishRequest request) {
        return Result.success(parkingSlotService.publishSlot(request));
    }

    @PostMapping("/stopSlot")
    public Result<String> stopSlot(@RequestBody StopSlotRequest request) {
        parkingSlotService.stopSlot(request);
        return Result.success("车位：" + request.getSlotId() + "的时段："+request.getStopStartTime()+"-"+request.getStopEndTime()+"已停用");
    }

    @PostMapping("/startSlot")
    public Result<String> startSlot(@RequestBody startSlotRequest request) {
        parkingSlotService.startSlot(request);
        return Result.success("车位：" + request.getSlotId() + "的时段："+request.getStartTime()+"-"+request.getStartEndTime()+"已启用");
    }

    @PutMapping("/update")
    public Result<String> updateSlot(@RequestBody UpdateSlotRequest request){
        parkingSlotService.updateSlot(request);
        return Result.success("车位"+request.getSlotId()+"修改成功");
    }

    @GetMapping("/list/{ownerId}")
    public Result<List<ParkingSlot>> list(@PathVariable Long ownerId){
        return Result.success(parkingSlotService.list(ownerId));
    }

    @GetMapping("/detail")
    public Result<ParkingSlotDetailVO> detail(@RequestBody ParkingSlotDetailRequest request) {
        return Result.success(parkingSlotService.detail(request));
    }
}