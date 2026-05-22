package com.huang.parkingshare.mq;

import com.huang.parkingshare.config.RabbitMQConfig;
import com.huang.parkingshare.document.ParkingSlotDocument;
import com.huang.parkingshare.entity.ParkingSlot;
import com.huang.parkingshare.mapper.ParkingSlotMapper;
import com.huang.parkingshare.repository.ParkingSlotEsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ParkingSlotSyncConsumer {

    @Autowired
    private ParkingSlotMapper parkingSlotMapper;

    @Autowired
    private ParkingSlotEsRepository esRepository;

    @RabbitListener(queues = RabbitMQConfig.PARKING_SLOT_SYNC_QUEUE)
    public void handleSync(ParkingSlotSyncMessage message) {
        Long slotId = message.getSlotId();
        String operation = message.getOperation();

        try {
            if ("CREATE".equals(operation) || "UPDATE".equals(operation)) {
                // 从 MySQL 查询最新车位信息
                ParkingSlot slot = parkingSlotMapper.selectById(slotId);
                if (slot == null || slot.getStatus() == 0) {
                    // 车位不存在或已停用，从 ES 删除
                    esRepository.deleteById(slotId);
                    log.info("车位 {} 不存在或停用，已从 ES 删除", slotId);
                    return;
                }
                // 转换为 ES 文档
                ParkingSlotDocument doc = new ParkingSlotDocument();
                doc.setId(slot.getId());
                doc.setOwnerId(slot.getOwnerId());
                doc.setAddress(slot.getAddress());
                doc.setLocation(new GeoPoint(slot.getLatitude().doubleValue(), slot.getLongitude().doubleValue()));
                doc.setBasePricePerHour(slot.getBasePricePerHour());
                doc.setStatus(slot.getStatus());
                doc.setStartTime(slot.getStartTime().toString());
                doc.setEndTime(slot.getEndTime().toString());
                esRepository.save(doc);
                log.info("车位 {} 已同步到 ES", slotId);
            } else if ("DELETE".equals(operation)) {
                esRepository.deleteById(slotId);
                log.info("车位 {} 已从 ES 删除", slotId);
            }
        } catch (Exception e) {
            log.error("同步车位 {} 到 ES 失败: {}", slotId, e.getMessage(), e);
            // 可抛出异常让 RabbitMQ 重试（需配置重试策略）
            throw new RuntimeException("ES 同步失败", e);
        }
    }
}