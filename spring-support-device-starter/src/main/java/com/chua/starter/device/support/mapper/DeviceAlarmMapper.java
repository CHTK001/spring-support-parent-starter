package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceAlarm;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备告警 映射器
 *
 * @since 4.0.0.51
 * @author CH
 */
@Mapper
public interface DeviceAlarmMapper extends BaseMapper<DeviceAlarm> {
}
