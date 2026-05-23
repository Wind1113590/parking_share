package com.huang.parkingshare.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huang.parkingshare.entity.ParkingSlot;
import com.huang.parkingshare.entity.TimeSlice;
import com.huang.parkingshare.mapper.TimeSliceMapper;
import com.huang.parkingshare.util.SnowflakeIdWorker;
import com.huang.parkingshare.util.TimeSliceGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class TimeSliceAsyncService {

    @Autowired
    private TimeSliceMapper timeSliceMapper;

    @Autowired
    private RedisTimeSliceService redisTimeSliceService;

    @Autowired
    private SnowflakeIdWorker snowflakeIdWorker;

    private static final int INIT_FUTURE_DAYS = 7;

    @Async  // 使用虚拟线程执行
    public void generateTimeSlicesAsync(ParkingSlot slot) {
        try {
            log.info("执行线程: " + Thread.currentThread());

            // 1. 生成未来7天的日期列表
            LocalDate today = LocalDate.now();
            List<LocalDate> dateList = TimeSliceGenerator.getDateRange(today, INIT_FUTURE_DAYS);

            // 2. 获取时间片模板
            List<int[]> sliceTemplate = TimeSliceGenerator.generateTimeSlices(
                    slot.getStartTime(), slot.getEndTime());
            List<Integer> startMinutes = new ArrayList<>();
            for (int[] slice : sliceTemplate) {
                startMinutes.add(slice[0]);
            }

            // 3. 遍历每一天，插入MySQL + Redis
            for (LocalDate date : dateList) {
                List<TimeSlice> slices = new ArrayList<>();
                for (int[] interval : sliceTemplate) {
                    TimeSlice ts = new TimeSlice();
                    ts.setId(snowflakeIdWorker.nextId());
                    ts.setSlotId(slot.getId());
                    ts.setSliceDate(date);
                    ts.setStartMinute(interval[0]);
                    ts.setEndMinute(interval[1]);
                    ts.setStatus(0);
                    ts.setOrderId(null);
                    ts.setVersion(0);
                    slices.add(ts);
                }
                // 批量插入MySQL
                if (!slices.isEmpty()) {
                    timeSliceMapper.batchInsert(slices);
                }
                // 初始化Redis
                redisTimeSliceService.initTimeSlicesForDay(slot.getId(), date, startMinutes);
            }
            // 可选：更新车位表的“初始化状态”字段为完成（略）
        } catch (Exception e) {
            // 记录错误日志，后续可增加重试或补偿任务
            // log.error("生成车位时间片失败, slotId: {}", slot.getId(), e);
            e.printStackTrace();
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW) // 新事务，独立于主事务
    public void updateTimeSliceStatusAfterLock(Long slotId, LocalDate date, List<Integer> minutes, Long orderId) {
        try {
            // 批量更新 time_slice 表状态为 1（锁定中）
            List<TimeSlice> slices = timeSliceMapper.selectList(
                    new LambdaQueryWrapper<TimeSlice>()
                            .eq(TimeSlice::getSlotId, slotId)
                            .eq(TimeSlice::getSliceDate, date)
                            .in(TimeSlice::getStartMinute, minutes)
            );
            if (!slices.isEmpty()) {
                slices.forEach(slice -> {
                    slice.setStatus(1);
                    slice.setOrderId(orderId);
                });
                // 使用 MyBatis-Plus 的 updateBatchById 或自定义批量更新
                timeSliceMapper.updateBatchById(slices);
            }
        } catch (Exception e) {
            log.error("异步更新 MySQL 时间片状态失败, slotId={}, date={}, minutes={}", slotId, date, minutes, e);
            // 可以写入失败记录表，由定时任务重试
        }
    }

    /**
     * 将 MySQL 中对应时间片的状态改回 0（空闲），order_id 清空
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateTimeSliceStatusToFree(Long slotId, LocalDate date, List<Integer> minutes, Long orderId) {
        LambdaQueryWrapper<TimeSlice> wrapper = new LambdaQueryWrapper<TimeSlice>()
                .eq(TimeSlice::getSlotId, slotId)
                .eq(TimeSlice::getSliceDate, date)
                .in(TimeSlice::getStartMinute, minutes)
                .eq(TimeSlice::getOrderId, orderId); // 只更新属于当前订单的时间片
        List<TimeSlice> slices = timeSliceMapper.selectList(wrapper);
        if (!slices.isEmpty()) {
            slices.forEach(slice -> {
                slice.setStatus(0);
                slice.setOrderId(null);
            });
            timeSliceMapper.updateBatchById(slices); // 复用之前的批量更新方法
        }
    }

    /**
     * 将 MySQL 中对应时间片的状态改回 0（空闲），order_id 清空
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateTimeSliceStatusToReserved(Long slotId, LocalDate date, List<Integer> minutes, Long orderId) {
        LambdaQueryWrapper<TimeSlice> wrapper = new LambdaQueryWrapper<TimeSlice>()
                .eq(TimeSlice::getSlotId, slotId)
                .eq(TimeSlice::getSliceDate, date)
                .in(TimeSlice::getStartMinute, minutes)
                .eq(TimeSlice::getOrderId, orderId); // 只更新属于当前订单的时间片
        List<TimeSlice> slices = timeSliceMapper.selectList(wrapper);
        if (!slices.isEmpty()) {
            slices.forEach(slice -> {
                slice.setStatus(2);
                slice.setOrderId(null);
            });
            timeSliceMapper.updateBatchById(slices); // 复用之前的批量更新方法
        }
    }
}