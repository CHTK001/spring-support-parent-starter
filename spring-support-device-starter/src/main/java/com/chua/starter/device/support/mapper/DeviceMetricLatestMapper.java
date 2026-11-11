package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceMetricLatest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备环境指标快照映射器。
 *
 * @since 4.0.0.54
 * @author CH
 */
@Mapper
public interface DeviceMetricLatestMapper extends BaseMapper<DeviceMetricLatest> {
}
