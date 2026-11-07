package com.chua.starter.device.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.device.support.entity.DeviceGroupDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备分组与设备的关联 Mapper。
 *
 * <p>除通用 CRUD 外，提供按分组查询设备（按 device_order 排序）等关联查询。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Mapper
public interface DeviceGroupDeviceMapper extends BaseMapper<DeviceGroupDevice> {

    /**
     * 查询某分组的全部设备（按 device_order 排序）。
     *
     * @param groupId 分组标识
     * @return 设备列表
     */
    List<DeviceGroupDevice> findByGroupId(@Param("groupId") String groupId);

    /**
     * 清空某分组的全部设备。
     *
     * @param groupId 分组标识
     * @return 影响行数
     */
    int deleteByGroupId(@Param("groupId") String groupId);

    /**
     * 清空某设备的全部分组。
     *
     * @param deviceId 设备标识
     * @return 影响行数
     */
    int deleteByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 查询某分组的全部设备标识。
     *
     * @param groupId 分组标识
     * @return 设备标识列表
     */
    List<String> findDeviceIds(@Param("groupId") String groupId);
}
