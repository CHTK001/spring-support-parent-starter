package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceAccessRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 门禁出入记录 映射器
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface DeviceAccessRecordMapper extends BaseMapper<DeviceAccessRecord> {
}