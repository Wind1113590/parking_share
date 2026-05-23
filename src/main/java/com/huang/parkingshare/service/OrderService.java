package com.huang.parkingshare.service;

import com.huang.parkingshare.config.RabbitMQConfig;
import com.huang.parkingshare.dto.OrderCreateRequest;
import com.huang.parkingshare.entity.Order;
import com.huang.parkingshare.entity.ParkingSlot;
import com.huang.parkingshare.mapper.OrderMapper;
import com.huang.parkingshare.mapper.ParkingSlotMapper;
import com.huang.parkingshare.component.RedisLuaScript;
import com.huang.parkingshare.util.SnowflakeIdWorker;
import com.huang.parkingshare.util.TimeSliceGenerator;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private ParkingSlotMapper parkingSlotMapper;

    @Autowired
    private RedisLuaScript redisLuaScript;
    @Autowired
    @Qualifier("customStringRedisTemplate")  // 注意指定名称
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String SLICE_KEY_PREFIX = "slice:slot:";
    private static final int IDEMPOTENT_TTL_MINUTES = 5;
    @Autowired
    private TimeSliceAsyncService timeSliceAsyncService;

    @Transactional
    public Order createOrder(OrderCreateRequest request) {
        // 1. 幂等性校验（基于 idempotentToken）
        String idempotentKey = "order:create:" + request.getIdempotentToken();
        Boolean already = redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_MINUTES, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(already)) {
            throw new RuntimeException("请勿重复提交订单");
        }

        // 2. 参数校验（时间、时长）
        validateTime(request.getStartTime(), request.getEndTime());

        // 3. 查询车位信息
        ParkingSlot slot = parkingSlotMapper.selectById(request.getSlotId());
        if (slot == null || slot.getStatus() != 1) {
            throw new RuntimeException("车位不存在或已停用");
        }

        // 4. 检查预约时间是否在车位的可预约时段内（每日 startTime ~ endTime）
        LocalTime startLocal = request.getStartTime().toLocalTime();
        LocalTime endLocal = request.getEndTime().toLocalTime();
        if (startLocal.isBefore(slot.getStartTime()) || endLocal.isAfter(slot.getEndTime())) {
            throw new RuntimeException("预约时间超出车位可预约时段");
        }

        // 5. 获取需要锁定的分钟列表
        List<Integer> minutes = TimeSliceGenerator.getRequiredStartMinutes(
                request.getStartTime(), request.getEndTime());
        if (minutes.isEmpty()) {
            throw new RuntimeException("预约时间过短");
        }
        LocalDate date = request.getStartTime().toLocalDate();
        String redisKey = SLICE_KEY_PREFIX + slot.getId() + ":" + date.toString();


        // 6. 分布式锁（粗粒度，避免多订单同时锁同一车位同一日期） 不用加了影响性能


        // 7. Lua 脚本原子锁定 Redis 时间片
        boolean success = redisLuaScript.lockTimeSlices(redisKey, minutes);
        if (!success) {
            throw new RuntimeException("所选时间段已被其他用户预约，请重新选择");
        }

        // 8. 计算总价（精确到分钟）
        long minutesDuration = Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
        BigDecimal hours = BigDecimal.valueOf(minutesDuration).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        BigDecimal totalPrice = slot.getBasePricePerHour().multiply(hours);

        // 9. 生成订单
        Order order = new Order();
        order.setUserId(1L); // 实际应从登录用户获取，暂时写死，后续替换
        order.setSlotId(slot.getId());
        order.setStartTime(request.getStartTime());
        order.setEndTime(request.getEndTime());
        order.setTotalAmount(totalPrice);
        order.setStatus(0); // 待支付
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);

        //  异步更新 MySQL 时间片状态（注意：一定要在订单插入事务提交后执行，避免异步事务读不到订单ID）
        timeSliceAsyncService.updateTimeSliceStatusAfterLock(slot.getId(), date, minutes, order.getId());

        // 10. 发送延迟消息（15分钟后检查支付状态）
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_DELAY_EXCHANGE,
                RabbitMQConfig.ORDER_DELAY_ROUTING_KEY,
                order.getId()
        );

        // 11. 可选：将幂等token与订单ID关联（便于后续查询）
        redisTemplate.opsForValue().set(idempotentKey + ":orderId", String.valueOf(order.getId()), IDEMPOTENT_TTL_MINUTES, TimeUnit.MINUTES);

        return order;
    }

    private void validateTime(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new RuntimeException("起止时间不能为空");
        }
        if (start.isAfter(end)) {
            throw new RuntimeException("开始时间不能晚于结束时间");
        }
        if (start.isBefore(LocalDateTime.now())) {
            throw new RuntimeException("不能预约过去的时间");
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < 15) {
            throw new RuntimeException("预约时长至少15分钟");
        }
        if (minutes > 240) {
            throw new RuntimeException("单次预约不能超过4小时");
        }
        // 跨天校验（暂不支持）
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw new RuntimeException("暂不支持跨天预约");
        }
    }
}