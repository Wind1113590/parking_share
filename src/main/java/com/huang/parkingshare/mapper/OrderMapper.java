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
}