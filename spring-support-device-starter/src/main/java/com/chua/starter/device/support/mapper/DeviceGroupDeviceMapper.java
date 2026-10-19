package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceGroupDevice;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DeviceGroupDeviceMapper extends BaseMapper<DeviceGroupDevice> {

    @Select("SELECT d.* FROM device_group_device d WHERE d.group_id = #{groupId} ORDER BY d.device_order")
    List<DeviceGroupDevice> findByGroupId(@Param("groupId") String groupId);

    @Delete("DELETE FROM device_group_device WHERE group_id = #{groupId}")
    int deleteByGroupId(@Param("groupId") String groupId);

    @Delete("DELETE FROM device_group_device WHERE device_id = #{deviceId}")
    int deleteByDeviceId(@Param("deviceId") String deviceId);

    @Select("SELECT device_id FROM device_group_device WHERE group_id = #{groupId}")
    List<String> findDeviceIds(@Param("groupId") String groupId);
}
