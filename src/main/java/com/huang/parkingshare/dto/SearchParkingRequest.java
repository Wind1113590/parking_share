package com.huang.parkingshare.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SearchParkingRequest {
    private Double latitude;     // 纬度
    private Double longitude;    // 经度
    private LocalDateTime startTime;  // 预约开始时间
    private LocalDateTime endTime;    // 预约结束时间
    private Double radiusKm = 3.0;    // 搜索半径，默认3公里
}