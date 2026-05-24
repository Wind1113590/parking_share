package com.huang.parkingshare.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
public class ParkingSlotDetailVO {
    private Long slotId;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal basePricePerHour;
    private LocalTime startTime;
    private LocalTime endTime;
    List<TimeSliceVO> timeSliceVOList;
}
