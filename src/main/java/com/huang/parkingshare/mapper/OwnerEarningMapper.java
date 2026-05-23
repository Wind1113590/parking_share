package com.huang.parkingshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huang.parkingshare.entity.Order;
import com.huang.parkingshare.entity.OwnerEarning;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

public interface OwnerEarningMapper extends BaseMapper<OwnerEarning> {
    @Update("UPDATE owner_earning SET status = 1, settle_date = #{settleDate} WHERE id = #{id} AND status = 0")
    int markAsSettled(@Param("id") Long id, @Param("settleDate") LocalDate settleDate);
}
