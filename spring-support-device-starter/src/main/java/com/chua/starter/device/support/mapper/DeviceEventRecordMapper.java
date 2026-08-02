package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceEventRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备事件记录 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface DeviceEventRecordMapper extends BaseMapper<DeviceEventRecord> {
}