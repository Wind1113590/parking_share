package com.huang.parkingshare.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StopSlotRequest {
    Long slotId;
    LocalDateTime stopStartTime;
    LocalDateTime stopEndTime;
}
