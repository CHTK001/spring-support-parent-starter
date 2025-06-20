package com.chua.starter.monitor.support.service;

import com.chua.starter.monitor.support.entity.MonitorDeviceInfo;
import com.chua.starter.monitor.support.entity.MonitorDeviceMetrics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备监控服务接口
 * @author CH
 * @since 2024/12/19
 */
public interface MonitorDeviceService {

    /**
     * 保存设备指标数据
     * @param metrics 设备指标数据
     * @return 是否保存成功
     */
    boolean saveDeviceMetrics(MonitorDeviceMetrics metrics);

    /**
     * 保存或更新设备基本信息
     * @param deviceInfo 设备基本信息
     * @return 是否保存成功
     */
    boolean saveOrUpdateDeviceInfo(MonitorDeviceInfo deviceInfo);

    /**
     * 根据IP和端口保存设备指标数据
     * @param ipAddress IP地址
     * @param port 端口
     * @param metrics 指标数据
     * @return 是否保存成功
     */
    boolean saveDeviceMetricsByIpAndPort(String ipAddress, Integer port, MonitorDeviceMetrics metrics);

    /**
     * 根据设备ID获取最新指标数据
     * @param deviceId 设备ID
     * @return 最新指标数据
     */
    MonitorDeviceMetrics getLatestMetrics(String deviceId);

    /**
     * 根据设备ID获取设备信息
     * @param deviceId 设备ID
     * @return 设备信息
     */
    MonitorDeviceInfo getDeviceInfo(String deviceId);

    /**
     * 根据IP和端口获取设备信息
     * @param ipAddress IP地址
     * @param port 端口
     * @return 设备信息
     */
    MonitorDeviceInfo getDeviceInfoByIpAndPort(String ipAddress, Integer port);

    /**
     * 获取所有在线设备
     * @return 在线设备列表
     */
    List<MonitorDeviceInfo> getOnlineDevices();

    /**
     * 获取启用监控的设备
     * @return 启用监控的设备列表
     */
    List<MonitorDeviceInfo> getMonitorEnabledDevices();

    /**
     * 更新设备在线状态
     * @param deviceId 设备ID
     * @param online 是否在线
     * @return 是否更新成功
     */
    boolean updateDeviceOnlineStatus(String deviceId, boolean online);

    /**
     * 清理过期的指标数据
     * @param beforeTime 时间点
     * @return 清理的记录数
     */
    int cleanExpiredMetrics(LocalDateTime beforeTime);

    /**
     * 处理设备心跳
     * @param deviceId 设备ID
     * @param ipAddress IP地址
     * @param port 端口
     * @return 是否处理成功
     */
    boolean handleDevicePing(String deviceId, String ipAddress, Integer port);
}
