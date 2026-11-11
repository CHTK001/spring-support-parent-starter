package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceVehicle;
import org.apache.ibatis.annotations.Mapper;

/**
 * 车辆档案映射器。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Mapper
public interface DeviceVehicleMapper extends BaseMapper<DeviceVehicle> {
}
