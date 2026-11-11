package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceAttendanceRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考勤记录映射器。
 *
 * @since 4.0.0.52
 * @author CH
 */
@Mapper
public interface DeviceAttendanceRecordMapper extends BaseMapper<DeviceAttendanceRecord> {
}
