package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceVehiclePassCursor;
import org.apache.ibatis.annotations.Mapper;

/**
 * 过车拉取游标映射器。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Mapper
public interface DeviceVehiclePassCursorMapper extends BaseMapper<DeviceVehiclePassCursor> {
}
