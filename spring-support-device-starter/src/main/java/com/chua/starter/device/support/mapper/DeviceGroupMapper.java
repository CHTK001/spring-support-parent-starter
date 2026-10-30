package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceGroupMapper extends BaseMapper<DeviceGroup> {

    /**
     * 统计某分组下的设备数量。
     *
     * @param groupId 分组标识
     * @return 设备数量
     */
    int countDevices(@Param("groupId") String groupId);

    /**
     * 查询设备所在的全部组标识。
     *
     * @param deviceId 设备标识
     * @return 组标识列表
     */
    List<String> findGroupIdsByDeviceId(@Param("deviceId") String deviceId);
}
