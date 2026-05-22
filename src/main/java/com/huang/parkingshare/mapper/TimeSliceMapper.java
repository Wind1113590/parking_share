package com.huang.parkingshare.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huang.parkingshare.entity.TimeSlice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TimeSliceMapper extends BaseMapper<TimeSlice> {

    /**
     * 批量插入时间片
     * @param list TimeSlice 列表
     * @return 插入行数
     */
    int batchInsert(@Param("list") List<TimeSlice> list);
}