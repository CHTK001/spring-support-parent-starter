package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceTaskState;
import org.apache.ibatis.annotations.Mapper;

/**
* 设备任务状态 映射器
*
* @since 4.0.0.42
* @author CH
 */
@Mapper
public interface DeviceTaskStateMapper extends BaseMapper<DeviceTaskState> {
}