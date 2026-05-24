package com.huang.parkingshare.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ParkingSlotDetailRequest {
    Long slotId;
    LocalDate sliceDate;
}
