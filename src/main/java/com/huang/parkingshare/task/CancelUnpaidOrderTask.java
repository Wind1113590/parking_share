package com.huang.parkingshare.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huang.parkingshare.entity.Order;
import com.huang.parkingshare.entity.UnpaidOrderTask;
import com.huang.parkingshare.mapper.OrderMapper;
import com.huang.parkingshare.mapper.UnpaidOrderTaskMapper;
import com.huang.parkingshare.service.RedisTimeSliceService;
import com.huang.parkingshare.service.TimeSliceAsyncService;
import com.huang.parkingshare.util.TimeSliceGenerator;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class CancelUnpaidOrderTask {

    @Autowired
    private UnpaidOrderTaskMapper taskMapper;
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private TimeSliceAsyncService timeSliceAsyncService;
    @Autowired
    private RedisTimeSliceService redisTimeSliceService;

    private static final int BATCH_SIZE = 100;

    @Scheduled(cron = "30 * * * * ?") // 每5分钟执行一次
    @SchedulerLock(name = "settleUnpaidOrderLock", lockAtLeastFor = "30s", lockAtMostFor = "5m")
    public void cancelExpiredOrders() {
        log.info("开始扫描待取消的未支付订单...");
        LocalDateTime now = LocalDateTime.now();

        // 1. 查询需要取消的任务（到了取消时间且状态为待取消）
        List<UnpaidOrderTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<UnpaidOrderTask>()
                        .le(UnpaidOrderTask::getShouldCancelTime, now)
                        .eq(UnpaidOrderTask::getStatus, 0)
                        .last("limit " + BATCH_SIZE)
        );

        if (tasks.isEmpty()) {
            log.info("没有需要取消的订单");
            return;
        }

        for (UnpaidOrderTask task : tasks) {
            cancelOneOrder(task);
        }
    }

    /**
     * 取消单个订单（幂等，使用乐观锁）
     */
    private void cancelOneOrder(UnpaidOrderTask task) {
        Long orderId = task.getOrderId();

        // 2. 先尝试更新订单状态：只有 status='PENDING' 才能改为 'CANCELLED'
        //    使用数据库乐观锁，防止与支付回调并发冲突
        int updateOrderRows = orderMapper.updateStatus(orderId, 0, 4);
        if (updateOrderRows == 0) {
            // 订单已经不是 pending 状态（可能已经支付或已被取消），任务标记为已完成
            log.info("订单 {} 状态已变更（可能已支付），任务忽略", orderId);
            task.setStatus(2);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
            return;
        }

        // 3. 订单成功更新为 CANCELLED，开始回滚资源（库存、优惠券、车位锁定等）
        try {
            Order order = orderMapper.selectById(orderId);

            // 4. 回滚 Redis 中的时间片状态：将状态从 "1"（锁定中）改回 "0"（空闲）
            LocalDate date = order.getStartTime().toLocalDate();
            List<Integer> minutes = TimeSliceGenerator.getRequiredStartMinutes(
                    order.getStartTime(), order.getEndTime());
            boolean rollbackSuccess = redisTimeSliceService.unlockTimeSlices(
                    order.getSlotId(), date, minutes);
            if (!rollbackSuccess) {
                log.error("回滚 Redis 时间片失败，需要人工处理。slotId={}, date={}, minutes={}",
                        order.getSlotId(), date, minutes);
                // 这里可以发送告警或记录失败表，后续补偿
            }

            // 同步更新 MySQL 中对应时间片的状态（保持最终一致性）
            timeSliceAsyncService.updateTimeSliceStatusToFree(order.getSlotId(), date, minutes);

            task.setStatus(1);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);

            log.info("定时任务兜底：订单超时取消成功，已释放时间片。orderId={}", orderId);
        } catch (Exception e) {
            log.error("定时任务兜底：订单 {} 取消成功但回滚资源失败", orderId, e);
            // 更新任务状态为失败，记录错误信息，等待下次重试
            task.setLastError(e.getMessage());
            task.setStatus(3);
            task.setRetryCount(task.getRetryCount()+1);
            taskMapper.updateById(task);
            // 注意：此时订单已经是 CANCELLED，但资源未释放，需要人工介入或下次重试释放
            // 建议增加独立的资源释放重试任务
        }
    }
}