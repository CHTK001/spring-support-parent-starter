package com.chua.report.client.starter.service;

import com.chua.report.client.starter.pojo.DeviceMetrics;

/**
 * 数据推送服务接口
 * @author CH
 * @since 2024/12/19
 */
public interface ReportPushService {

    /**
     * 推送设备指标数据
     * @param metrics 设备指标数据
     * @return 是否推送成功
     */
    boolean pushDeviceMetrics(DeviceMetrics metrics);

    /**
     * 推送设备基本信息
     * @param deviceInfo 设备基本信息
     * @return 是否推送成功
     */
    boolean pushDeviceInfo(DeviceMetrics deviceInfo);

    /**
     * 测试连接
     * @return 是否连接成功
     */
    boolean testConnection();
}
