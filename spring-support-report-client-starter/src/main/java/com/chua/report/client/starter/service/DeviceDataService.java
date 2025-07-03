package com.chua.report.client.starter.service;


import com.chua.report.client.starter.pojo.DeviceMetrics;

/**
 * 设备数据服务接口
 * @author CH
 * @since 2024/12/19
 */
public interface DeviceDataService {

    /**
     * 获取设备指标数据
     * @return 设备指标数据
     */
    DeviceMetrics getDeviceMetrics();

    /**
     * 获取设备基本信息
     * @return 设备基本信息
     */
    DeviceMetrics getDeviceInfo();

    /**
     * 检查设备是否在线
     * @return 是否在线
     */
    boolean isDeviceOnline();
}
