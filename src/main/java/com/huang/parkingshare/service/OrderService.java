package com.huang.parkingshare.service;

import com.huang.parkingshare.config.RabbitMQConfig;
import com.huang.parkingshare.dto.OrderCreateRequest;
import com.huang.parkingshare.entity.Order;
import com.huang.parkingshare.entity.ParkingSlot;
import com.huang.parkingshare.entity.PaymentTransaction;
import com.huang.parkingshare.mapper.OrderMapper;
import com.huang.parkingshare.mapper.ParkingSlotMapper;
import com.huang.parkingshare.component.RedisLuaScript;
import com.huang.parkingshare.mapper.PaymentTransactionMapper;
import com.huang.parkingshare.util.SnowflakeIdWorker;
import com.huang.parkingshare.util.TimeSliceGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
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
    private PaymentTransactionMapper paymentTransactionMapper;

    @Autowired
    private RedisLuaScript redisLuaScript;
    @Autowired
    private SnowflakeIdWorker snowflakeIdWorker;


    @Autowired
    @Qualifier("customStringRedisTemplate")  // 注意指定名称
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTimeSliceService redisTimeSliceService;
    @Autowired
    private OwnerEarningService ownerEarningService;

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
            throw new IllegalArgumentException("预约时间超出车位可预约时段");
        }

        // 5. 获取需要锁定的分钟列表
        List<Integer> minutes = TimeSliceGenerator.getRequiredStartMinutes(
                request.getStartTime(), request.getEndTime());
        if (minutes.isEmpty()) {
            throw new IllegalArgumentException("预约时间过短");
        }
        LocalDate date = request.getStartTime().toLocalDate();
        String redisKey = SLICE_KEY_PREFIX + slot.getId() + ":" + date.toString();


        // 6. 分布式锁（粗粒度，避免多订单同时锁同一车位同一日期） 不用加了影响性能


        // 7. Lua 脚本原子锁定 Redis 时间片
        boolean success = redisLuaScript.lockTimeSlices(redisKey, minutes);
        if (!success) {
            throw new RuntimeException("所选时间段已被其他用户预约，请重新选择");
        }

        // 8. 计算总价（精确到分钟）                33  50   30-45 45-60
        long minutesDuration = minutes.getLast() - minutes.getFirst() + 15;
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
            throw new IllegalArgumentException("起止时间不能为空");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        if (start.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("不能预约过去的时间");
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < 15) {
            throw new IllegalArgumentException("预约时长至少15分钟");
        }
        if (minutes > 240) {
            throw new IllegalArgumentException("单次预约不能超过4小时");
        }
        // 跨天校验（暂不支持）
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw new IllegalArgumentException("暂不支持跨天预约");
        }
    }

    @Transactional
    public void payOrder(Long orderId) {

        // 1. 条件更新订单状态（原子操作）
        int updated = orderMapper.payOrder(orderId);
        if (updated == 0) {
            // 可能已经支付或超时取消，重新查询确认错误类型
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new RuntimeException("订单不存在");
            }
            if (order.getStatus() == 1) {
                throw new RuntimeException("订单已支付，请勿重复操作");
            }
            if (order.getStatus() == 4) {
                throw new RuntimeException("订单已超时取消，无法支付");
            }
            throw new RuntimeException("支付失败，请稍后重试");
        }

        // 2. 模拟调用第三方支付成功（实际应该是异步回调，这里简化同步）
        // 实际项目中，此处应发起支付请求，成功后收到回调再执行下面逻辑。
        // 为了演示，我们假设支付成功。
        Order order = orderMapper.selectById(orderId);

        // 3. 生成外部流水号（模拟）
        String outTradeNo = "SIM_" + snowflakeIdWorker.nextId();  // 例如 "SIM_" + SnowflakeIdWorker.nextId()

        // 4. 记录支付流水（幂等：使用 out_trade_no 唯一索引防重）
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(orderId);
        transaction.setOutTradeNo(outTradeNo);
        transaction.setAmount(order.getTotalAmount());
        transaction.setStatus(1);   // 成功
        transaction.setCreateTime(LocalDateTime.now());
        paymentTransactionMapper.insert(transaction);


        // 3. 更新 Redis 时间片状态：从 "1" -> "2"
        // 重新查询获取起止时间
        LocalDate date = order.getStartTime().toLocalDate();
        List<Integer> minutes = TimeSliceGenerator.getRequiredStartMinutes(
                order.getStartTime(), order.getEndTime());
        boolean redisOk = redisTimeSliceService.bookTimeSlices(order.getSlotId(), date, minutes);
        if (!redisOk) {
            log.error("预约时间片失败，订单号：{}", orderId);
            // 不抛异常，订单已支付成功，可依赖定时任务补偿 Redis
        }

        //异步 同步MySQL时间片状态
        timeSliceAsyncService.updateTimeSliceStatusToReserved(order.getSlotId(), date, minutes, orderId);

        log.info("订单 {} 支付成功", orderId);
    }

    @Transactional
    public void startUsing(Long orderId) {
        int updated = orderMapper.startUsing(orderId);
        if (updated == 0) {
            throw new RuntimeException("订单状态不是已支付，无法入场");
        }
        // 可选：记录入场时间到 Redis（用于计时）
    }

    @Transactional
    public void completeOrder(Long orderId) {
        // 1. 条件更新订单状态（仅当状态为 2 使用中时改为 3 已完成）
        int updated = orderMapper.completeOrder(orderId);
        if (updated == 0) {
            throw new RuntimeException("订单状态不是使用中，无法离场");
        }

        // 2. 获取订单和车位信息
        Order order = orderMapper.selectById(orderId);
        ParkingSlot slot = parkingSlotMapper.selectById(order.getSlotId());

        // 3. 计算超时费用并处理 Redis 时间片占用
        LocalDateTime actualEnd = order.getActualEndTime(); // 已在 completeOrder SQL 中设置
        LocalDateTime scheduledEnd = order.getEndTime();
        BigDecimal extraAmount = BigDecimal.ZERO;

        if (actualEnd.isAfter(scheduledEnd)) {

            // 获取超出的时间段需要占用的起始分钟列表
            List<Integer> extraMinutes = TimeSliceGenerator.getRequiredStartMinutes(scheduledEnd, actualEnd);
            LocalDate date = scheduledEnd.toLocalDate();

            // 原子操作：检查并锁定这些时间片为 "2"（仅当全部空闲）
            boolean occupied = redisTimeSliceService.occupyFreeTimeSlices(order.getSlotId(), date, extraMinutes);
            if (!occupied) {
                throw new RuntimeException("您的超时时间段已被其他用户预约，无法离场。请联系管理员或立即驶离。");
            }
            timeSliceAsyncService.updateTimeSliceStatusAfterLock(slot.getId(), date, extraMinutes, orderId);

            // 计算超时费用
            long overMinutes = Duration.between(scheduledEnd, actualEnd).toMinutes();
            long overSlices = (long) Math.ceil(overMinutes / 15.0);
            BigDecimal pricePerSlice = slot.getBasePricePerHour().divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
            extraAmount = pricePerSlice.multiply(BigDecimal.valueOf(overSlices));

            log.info("订单 {} 超时 {} 分钟，额外占用时间片 {} 个，需额外支付 {}", orderId, overMinutes, overSlices, extraAmount);
            // 实际应用中可能需要创建补缴支付流水，等待用户支付
            // 这里模拟支付成功，直接记录额外金额
        }

        // 4. 更新订单的实际支付金额（原金额 + 超时费）
        BigDecimal totalActualAmount = order.getTotalAmount().add(extraAmount);
        order.setActualAmount(totalActualAmount);
        orderMapper.updateById(order);

        // 5. 异步结算业主收益（按实际总收入结算）
        ownerEarningService.settleEarning(orderId);

        log.info("订单 {} 离场完成，预约费用 {}，超时费 {}，总计 {}", orderId, order.getTotalAmount(), extraAmount, totalActualAmount);
    }
}