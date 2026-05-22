package com.huang.parkingshare.service;

import com.huang.parkingshare.dto.ParkingSlotPublishDTO;
import com.huang.parkingshare.entity.ParkingSlot;
import com.huang.parkingshare.entity.TimeSlice;
import com.huang.parkingshare.mapper.ParkingSlotMapper;
import com.huang.parkingshare.mapper.TimeSliceMapper;
import com.huang.parkingshare.util.SnowflakeIdWorker;
import com.huang.parkingshare.util.TimeSliceGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ParkingSlotService {

    @Autowired
    private ParkingSlotMapper parkingSlotMapper;

    @Autowired
    private TimeSliceAsyncService timeSliceAsyncService;

    // 初始化未来天数（可以配置，这里默认7天）
    private static final int INIT_FUTURE_DAYS = 7;

    @Transactional
    public ParkingSlot publishSlot(ParkingSlotPublishDTO dto) {
        // 1. 插入车位表
        ParkingSlot slot = new ParkingSlot();
        slot.setOwnerId(dto.getOwnerId());
        slot.setAddress(dto.getAddress());
        slot.setLatitude(dto.getLatitude());
        slot.setLongitude(dto.getLongitude());
        slot.setBasePricePerHour(dto.getBasePricePerHour());
        slot.setStatus(1);   // 正常
        slot.setStartTime(dto.getStartTime());
        slot.setEndTime(dto.getEndTime());
        slot.setCreateTime(LocalDateTime.now());
        parkingSlotMapper.insert(slot);

        // 2. 异步生成时间片（虚拟线程执行，不等待）
        timeSliceAsyncService.generateTimeSlicesAsync(slot);

        return slot;
    }
}