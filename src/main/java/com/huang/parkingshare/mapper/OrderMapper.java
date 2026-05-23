package com.huang.parkingshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huang.parkingshare.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Update("UPDATE `order` SET status = #{newStatus}, pay_time = NOW() WHERE id = #{orderId} AND status = #{oldStatus}")
    int updateStatus(@Param("orderId") Long orderId,
                     @Param("oldStatus") Integer oldStatus,
                     @Param("newStatus") Integer newStatus);

    // 支付成功：仅当订单状态为 0（待支付）时更新为 1
    @Update("UPDATE `order` SET status = 1, pay_time = NOW() WHERE id = #{orderId} AND status = 0")
    int payOrder(Long orderId);

    // 超时取消：仅当订单状态为 0（待支付）时更新为 4
    @Update("UPDATE `order` SET status = 4, cancel_time = NOW() WHERE id = #{orderId} AND status = 0")
    int cancelOrder(Long orderId);

    // 入场：仅当状态为 1（已支付）时改为 2（使用中）
    @Update("UPDATE `order` SET status = 2 WHERE id = #{orderId} AND status = 1")
    int startUsing(@Param("orderId") Long orderId);

    // 离场：仅当状态为 2（使用中）时改为 3（已完成），并记录实际离场时间
    @Update("UPDATE `order` SET status = 3, actual_end_time = NOW() WHERE id = #{orderId} AND status = 2")
    int completeOrder(@Param("orderId") Long orderId);
}