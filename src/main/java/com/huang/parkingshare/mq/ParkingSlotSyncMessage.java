package com.huang.parkingshare.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParkingSlotSyncMessage implements Serializable {
    private Long slotId;      // 车位ID
    private Long ownerId;     // 业主ID（可选）
    private String operation; // "CREATE", "UPDATE", "DELETE"
}