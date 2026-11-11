package com.chua.starter.device.support.service;

import com.chua.starter.device.support.entity.DeviceMetricLatest;

import java.util.List;

/**
 * 设备环境指标服务。
 *
 * @since 4.0.0.54
 * @author CH
 */
public interface DeviceMetricService {

    /**
     * 查询设备最新指标快照。
     *
     * @param deviceId 设备 ID
     * @return 指标列表，按名称排序
     */
    List<DeviceMetricLatest> listLatest(String deviceId);

    /**
     * 立即采集单台设备指标。
     *
     * @param deviceId 设备 ID
     * @return 写入的指标行数
     */
    int collectDevice(String deviceId);

    /**
     * 采集某套凭据下全部可采集设备。
     *
     * @param credentialId 凭据 ID
     * @return 写入的指标行数
     */
    int collectByCredential(String credentialId);
}
