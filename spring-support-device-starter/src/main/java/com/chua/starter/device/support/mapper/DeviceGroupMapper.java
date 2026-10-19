package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DeviceGroupMapper extends BaseMapper<DeviceGroup> {

    @Select("SELECT COUNT(*) FROM device_group_device WHERE group_id = #{groupId}")
    int countDevices(@Param("groupId") String groupId);

    @Select("SELECT group_id FROM device_group_device WHERE device_id = #{deviceId}")
    List<String> findGroupIdsByDeviceId(@Param("deviceId") String deviceId);
}
