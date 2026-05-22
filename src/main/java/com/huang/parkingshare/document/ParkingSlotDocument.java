package com.huang.parkingshare.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

import java.math.BigDecimal;

@Data
@Document(indexName = "parking_slot")
@Setting(shards = 1, replicas = 0)  // 开发环境可简化
public class ParkingSlotDocument {
    @Id
    private Long id;
    private Long ownerId;
    private String address;
    @GeoPointField
    private GeoPoint location;   // 经纬度
    private BigDecimal basePricePerHour;
    private Integer status;       // 1正常 0停用
    private String startTime;     // 格式 "08:00:00"
    private String endTime;
}