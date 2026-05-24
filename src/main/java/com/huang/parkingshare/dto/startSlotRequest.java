package com.huang.parkingshare.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class startSlotRequest {
    Long slotId;
    LocalDateTime startTime;
    LocalDateTime startEndTime;
}
