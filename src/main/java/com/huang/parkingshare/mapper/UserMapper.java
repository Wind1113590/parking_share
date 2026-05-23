package com.huang.parkingshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huang.parkingshare.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE user SET balance = balance + #{amount} WHERE id = #{ownerId}")
    int addBalance(@Param("ownerId") Long ownerId, @Param("amount") BigDecimal amount);
}