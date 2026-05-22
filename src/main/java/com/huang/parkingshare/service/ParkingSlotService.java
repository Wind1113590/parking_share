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
    private TimeSliceMapper timeSliceMapper;

    @Autowired
    private RedisTimeSliceService redisTimeSliceService;

    @Autowired
    private SnowflakeIdWorker snowflakeIdWorker;

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

        // 2. 生成从今天开始未来 INIT_FUTURE_DAYS 天的日期列表
        LocalDate today = LocalDate.now();
        List<LocalDate> dateList = TimeSliceGenerator.getDateRange(today, INIT_FUTURE_DAYS);

        // 3. 针对每一天，生成时间片并存入 time_slice 表 + 初始化 Redis
        // 先获取该车位的每日时间片模板（用于插入数据库） 与startMinute列表（redis hash的key 用于区分时间片）
        List<int[]> sliceTemplate = TimeSliceGenerator.generateTimeSlices(
                dto.getStartTime(), dto.getEndTime());  // 日期只用于获取分钟，实际不存储

        List<Integer> startMinutes = new ArrayList<>();//用于写入redis hash的key 区分每个时间片
        for (int[] slice : sliceTemplate) {
            startMinutes.add(slice[0]);
        }

        for (LocalDate date : dateList) {
            // 3.1 生成该天的所有时间片记录
            List<TimeSlice> slices = new ArrayList<>();
            for (int[] interval : sliceTemplate) {
                TimeSlice ts = new TimeSlice();
                ts.setId(snowflakeIdWorker.nextId());
                ts.setSlotId(slot.getId());
                ts.setSliceDate(date);
                ts.setStartMinute(interval[0]);
                ts.setEndMinute(interval[1]);
                ts.setStatus(0);        // 空闲
                ts.setOrderId(null);
                ts.setVersion(0);
                slices.add(ts);
            }
            // 批量插入 time_slice 表
            timeSliceMapper.insert(slices);

            // 3.2 初始化 Redis Hash
            redisTimeSliceService.initTimeSlicesForDay(slot.getId(), date, startMinutes);
        }

        return slot;
    }
}