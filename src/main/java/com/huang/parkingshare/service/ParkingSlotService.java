package com.huang.parkingshare.service;

import com.huang.parkingshare.config.RabbitMQConfig;
import com.huang.parkingshare.document.ParkingSlotDocument;
import com.huang.parkingshare.dto.ParkingSlotPublishRequest;
import com.huang.parkingshare.dto.SearchParkingRequest;
import com.huang.parkingshare.entity.ParkingSlot;
import com.huang.parkingshare.mapper.ParkingSlotMapper;
import com.huang.parkingshare.mq.ParkingSlotSyncMessage;
import com.huang.parkingshare.repository.ParkingSlotEsRepository;
import com.huang.parkingshare.util.TimeSliceGenerator;
import com.huang.parkingshare.vo.ParkingSlotVO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class ParkingSlotService {

    @Autowired
    private ParkingSlotMapper parkingSlotMapper;

    @Autowired
    private TimeSliceAsyncService timeSliceAsyncService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ParkingSlotEsRepository esRepository;

    @Autowired
    private RedisTimeSliceService redisTimeSliceService;


    // 初始化未来天数（可以配置，这里默认7天）
    private static final int INIT_FUTURE_DAYS = 7;

    @Transactional
    public ParkingSlot publishSlot(ParkingSlotPublishRequest request) {
        // 1. 插入车位表
        ParkingSlot slot = new ParkingSlot();
        slot.setOwnerId(request.getOwnerId());
        slot.setAddress(request.getAddress());
        slot.setLatitude(request.getLatitude());
        slot.setLongitude(request.getLongitude());
        slot.setBasePricePerHour(request.getBasePricePerHour());
        slot.setStatus(1);   // 正常
        slot.setStartTime(request.getStartTime());
        slot.setEndTime(request.getEndTime());
        slot.setCreateTime(LocalDateTime.now());
        parkingSlotMapper.insert(slot);


        // 2. 发送异步消息（同步到 ES）
        ParkingSlotSyncMessage message = new ParkingSlotSyncMessage(slot.getId(), slot.getOwnerId(), "CREATE");
        rabbitTemplate.convertAndSend(RabbitMQConfig.PARKING_SLOT_SYNC_EXCHANGE,
                RabbitMQConfig.PARKING_SLOT_SYNC_ROUTING_KEY,
                message);

        // 2. 异步生成时间片（虚拟线程执行，不等待）
        timeSliceAsyncService.generateTimeSlicesAsync(slot);

        return slot;
    }

    public void updateSlot(ParkingSlot slot) {
        parkingSlotMapper.updateById(slot);
        // 发送异步更新消息
        ParkingSlotSyncMessage message = new ParkingSlotSyncMessage(slot.getId(), slot.getOwnerId(), "UPDATE");
        rabbitTemplate.convertAndSend(RabbitMQConfig.PARKING_SLOT_SYNC_EXCHANGE, RabbitMQConfig.PARKING_SLOT_SYNC_ROUTING_KEY, message);
    }

    public void deleteSlot(Long slotId) {
        parkingSlotMapper.deleteById(slotId);  // 或逻辑删除
        ParkingSlotSyncMessage message = new ParkingSlotSyncMessage(slotId, null, "DELETE");
        rabbitTemplate.convertAndSend(RabbitMQConfig.PARKING_SLOT_SYNC_EXCHANGE, RabbitMQConfig.PARKING_SLOT_SYNC_ROUTING_KEY, message);
    }

    public List<ParkingSlotVO> searchAvailableSlots(SearchParkingRequest request) {
        // 1. 参数校验
        validateRequest(request);

        // 2. ES 查询附近可用车位（status=1 且距离在半径内）
        Point point = new Point(request.getLongitude(), request.getLatitude());
        String distanceStr = request.getRadiusKm() + "km";
        List<ParkingSlotDocument> docs = esRepository.findAvailableNearby(point.getY(),point.getX(), distanceStr);
        if (docs.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 获取用户需要检查的时间片起始分钟列表（同一日内）
        LocalDate startDate = request.getStartTime().toLocalDate();
        LocalDate endDate = request.getEndTime().toLocalDate();
        if (!startDate.equals(endDate)) {
            throw new IllegalArgumentException("暂不支持跨天预约");
        }
        List<Integer> requiredMinutes = TimeSliceGenerator.getRequiredStartMinutes( //会向下取整获取对应的时间片 比如开始时间12:02 结束时间12:05 实际上分片就是12:00 - 12:05
                request.getStartTime(), request.getEndTime());

        // 4. 对每个车位，检查 Redis 中的时间片是否全部空闲
        List<ParkingSlotVO> available = new ArrayList<>();
        for (ParkingSlotDocument doc : docs) {
            boolean free = redisTimeSliceService.areTimeSlicesFree( //时间段里的每一个分片都需要可用
                    doc.getId(), startDate, requiredMinutes);
            if (!free) {
                continue;
            }

            // 计算总价（精确到分钟）
            long minutes = Duration.between(request.getStartTime(), request.getEndTime()).toMinutes();
            BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            BigDecimal totalPrice = doc.getBasePricePerHour().multiply(hours);

            // 计算距离（公里）
            double distance = calculateDistance(
                    request.getLatitude(), request.getLongitude(),
                    doc.getLocation().getLat(), doc.getLocation().getLon()
            );

            ParkingSlotVO vo = new ParkingSlotVO();
            vo.setSlotId(doc.getId());
            vo.setAddress(doc.getAddress());
            vo.setLatitude(BigDecimal.valueOf(doc.getLocation().getLat()));
            vo.setLongitude(BigDecimal.valueOf(doc.getLocation().getLon()));
            vo.setBasePricePerHour(doc.getBasePricePerHour());
            vo.setTotalPrice(totalPrice);
            vo.setDistanceKm(distance);
            vo.setStartTime(request.getStartTime());
            vo.setEndTime(request.getEndTime());
            available.add(vo);
        }

        // 按距离排序
        available.sort(Comparator.comparingDouble(ParkingSlotVO::getDistanceKm));
        return available;
    }

    private void validateRequest(SearchParkingRequest request) {
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new IllegalArgumentException("起止时间不能为空");
        }
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        if (request.getStartTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("不能预约过去的时间");
        }
        if (Duration.between(request.getStartTime(), request.getEndTime()).toMinutes() > 240) {
            throw new IllegalArgumentException("单次预约不能超过4小时");
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // 公里
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }


}