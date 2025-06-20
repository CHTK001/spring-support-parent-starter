package com.chua.starter.monitor.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.monitor.support.entity.MonitorDeviceInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备信息Mapper
 * @author CH
 * @since 2024/12/19
 */
@Mapper
public interface MonitorDeviceInfoMapper extends BaseMapper<MonitorDeviceInfo> {

    /**
     * 根据设备ID查询设备信息
     * @param deviceId 设备ID
     * @return 设备信息
     */
    MonitorDeviceInfo selectByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 根据IP地址和端口查询设备信息
     * @param ipAddress IP地址
     * @param port 端口
     * @return 设备信息
     */
    MonitorDeviceInfo selectByIpAndPort(
        @Param("ipAddress") String ipAddress,
        @Param("port") Integer port
    );

    /**
     * 查询所有在线设备
     * @return 在线设备列表
     */
    List<MonitorDeviceInfo> selectOnlineDevices();

    /**
     * 查询启用监控的设备
     * @return 启用监控的设备列表
     */
    List<MonitorDeviceInfo> selectMonitorEnabledDevices();

    /**
     * 更新设备在线状态
     * @param deviceId 设备ID
     * @param status 状态
     * @return 更新记录数
     */
    int updateDeviceStatus(@Param("deviceId") String deviceId, @Param("status") Integer status);
}
