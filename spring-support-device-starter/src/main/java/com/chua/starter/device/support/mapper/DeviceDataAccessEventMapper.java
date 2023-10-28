package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceDataAccessEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceDataAccessEventMapper extends BaseMapper<DeviceDataAccessEvent> {
}