package com.huang.parkingshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huang.parkingshare.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}