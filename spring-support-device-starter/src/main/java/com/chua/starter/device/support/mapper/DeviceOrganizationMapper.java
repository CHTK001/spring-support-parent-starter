package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceOrganization;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备组织 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface DeviceOrganizationMapper extends BaseMapper<DeviceOrganization> {
}