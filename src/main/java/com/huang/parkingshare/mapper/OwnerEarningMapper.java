package com.huang.parkingshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huang.parkingshare.entity.Order;
import com.huang.parkingshare.entity.OwnerEarning;
import com.huang.parkingshare.vo.OrderVO;
import com.huang.parkingshare.vo.OwnerEarningVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

public interface OwnerEarningMapper extends BaseMapper<OwnerEarning> {
    @Update("UPDATE owner_earning SET status = 1, settle_date = #{settleDate} WHERE id = #{id} AND status = 0")
    int markAsSettled(@Param("id") Long id, @Param("settleDate") LocalDate settleDate);

    Page<OwnerEarningVO> selectOwnerEarningPage(Page<?> page,
                                         @Param("ownerId") Long ownerId,
                                         @Param("status") Integer status);
}
