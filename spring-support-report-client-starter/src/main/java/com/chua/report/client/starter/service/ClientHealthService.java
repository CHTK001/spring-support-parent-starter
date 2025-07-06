package com.chua.report.client.starter.service;

import com.chua.report.client.starter.pojo.ClientHealthInfo;
import com.chua.report.client.starter.pojo.ClientHealthReport;

/**
 * 客户端健康状态服务接口
 * @author CH
 * @since 2024/12/19
 */
public interface ClientHealthService {

    /**
     * 更新客户端健康状态
     * @param deviceId 设备ID
     * @param deviceName 设备名称
     */
    void updateHealthStatus(String deviceId, String deviceName);

    /**
     * 检查客户端是否健康
     * @return 是否健康
     */
    boolean isHealthy();

    /**
     * 获取客户端健康信息
     * @return 健康信息
     */
    ClientHealthInfo getHealthInfo();

    /**
     * 获取最后更新时间
     * @return 最后更新时间戳
     */
    long getLastUpdateTime();

    /**
     * 获取健康状态有效期（秒）
     * @return 有效期
     */
    long getHealthValidityPeriod();

    /**
     * 检查健康状态是否过期
     * @return 是否过期
     */
    boolean isHealthExpired();

    /**
     * 重置健康状态
     */
    void resetHealth();

    /**
     * 获取健康状态上报对象
     * @return 健康状态上报对象，如果没有健康信息则返回null
     */
    ClientHealthReport getHealthReport();
}
