package com.huang.parkingshare.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OrderCreateRequest {
    @NotNull
    private Long slotId;// 车位ID
    @NotNull
    private LocalDateTime startTime;
    @NotNull
    private LocalDateTime endTime;
}