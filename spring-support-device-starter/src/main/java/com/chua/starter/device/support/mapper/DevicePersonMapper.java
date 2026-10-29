package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DevicePerson;
import org.apache.ibatis.annotations.Mapper;

/**
 * 厂商人员 映射器
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface DevicePersonMapper extends BaseMapper<DevicePerson> {
}
