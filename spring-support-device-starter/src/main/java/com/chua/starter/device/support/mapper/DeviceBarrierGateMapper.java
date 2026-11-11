package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceBarrierGate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 车闸点位映射器。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Mapper
public interface DeviceBarrierGateMapper extends BaseMapper<DeviceBarrierGate> {
}
