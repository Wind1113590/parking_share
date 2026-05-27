package com.huang.parkingshare.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huang.parkingshare.entity.Order;
import com.huang.parkingshare.entity.OwnerEarning;
import com.huang.parkingshare.entity.ParkingSlot;
import com.huang.parkingshare.mapper.OrderMapper;
import com.huang.parkingshare.mapper.OwnerEarningMapper;
import com.huang.parkingshare.mapper.ParkingSlotMapper;
import com.huang.parkingshare.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OwnerEarningService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OwnerEarningMapper ownerEarningMapper;

    @Autowired
    private ParkingSlotMapper parkingSlotMapper;

    @Autowired
    private UserMapper userMapper;

    static Double PLATFORM_COMMISSION_RATE = 0.1;


    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settleEarning(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != 3) return;

        // 防止重复结算
        LambdaQueryWrapper<OwnerEarning> wrapper = new LambdaQueryWrapper<OwnerEarning>()
                .eq(OwnerEarning::getOrderId, orderId);
        if (ownerEarningMapper.selectCount(wrapper) > 0) return;

        ParkingSlot slot = parkingSlotMapper.selectById(order.getSlotId());
        if (slot == null) return;

        // 实际结算金额（如果 actual_amount 不为空则用它，否则用预约时的 total_amount）
        BigDecimal finalAmount = order.getActualAmount() != null ? order.getActualAmount() : order.getTotalAmount();
        BigDecimal ownerIncome = finalAmount.multiply(BigDecimal.valueOf(1 - PLATFORM_COMMISSION_RATE));

        OwnerEarning earning = new OwnerEarning();
        earning.setOwnerId(slot.getOwnerId());
        earning.setOrderId(orderId);
        earning.setAmount(ownerIncome);
        earning.setStatus(0); // 待结算
        earning.setSettleDate(null);
        ownerEarningMapper.insert(earning);

        // 可选：实时更新业主钱包余额
        //userMapper.addBalance(slot.getOwnerId(), ownerIncome);
    }
}