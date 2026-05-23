package com.huang.parkingshare.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huang.parkingshare.config.RabbitMQConfig;
import com.huang.parkingshare.entity.Order;
import com.huang.parkingshare.entity.TimeSlice;
import com.huang.parkingshare.mapper.OrderMapper;
import com.huang.parkingshare.mapper.TimeSliceMapper;
import com.huang.parkingshare.service.RedisTimeSliceService;
import com.huang.parkingshare.service.TimeSliceAsyncService;
import com.huang.parkingshare.util.TimeSliceGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OrderTimeoutConsumer {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RedisTimeSliceService redisTimeSliceService;

    @Autowired
    private TimeSliceAsyncService timeSliceAsyncService;

    /**
     * 监听死信队列，处理超时未支付的订单
     *
     * @param orderId 订单ID
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_DEAD_QUEUE)
    @Transactional
    public void handleOrderTimeout(Long orderId) {
        log.info("收到订单超时消息，订单ID: {}", orderId);

        // 1. 查询订单，仅当状态为“待支付(0)”时才处理
        Order order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != 0) {
            log.info("订单不存在或状态不是待支付，跳过处理。orderId={}, status={}", orderId, order != null ? order.getStatus() : "null");
            return;
        }

        // 2. 二次确认是否真的超时（防止消息延迟触发过早）
        LocalDateTime expireTime = order.getCreateTime().plusMinutes(15);
        if (LocalDateTime.now().isBefore(expireTime)) {
            log.info("订单尚未超时，忽略本次处理。订单创建时间: {}", order.getCreateTime());
            return;
        }

        // 3. 更新订单状态为“已取消(4)”
        order.setStatus(4);
        order.setCancelTime(LocalDateTime.now());
        int updated = orderMapper.cancelOrder(orderId);
        if (updated == 0) {
            log.warn("订单取消失败，可能已被支付或其他操作修改。orderId={}", orderId);
            return;
        }

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

        // 5. 同步更新 MySQL 中对应时间片的状态（可选，但建议保持最终一致性）
        timeSliceAsyncService.updateTimeSliceStatusToFree(order.getSlotId(), date, minutes, order.getId());

        log.info("订单超时取消成功，已释放时间片。orderId={}", orderId);
    }


}