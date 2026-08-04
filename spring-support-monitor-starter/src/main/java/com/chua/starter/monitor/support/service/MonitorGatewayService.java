package com.chua.starter.monitor.support.service;

import com.chua.starter.monitor.support.entity.MsMonitorGateway;

import java.util.List;

/**
 * 监控网关 Service
 *
 * @author CH
 * @since 2026/8/4
 */
public interface MonitorGatewayService {

    /**
     * 创建
     *
     * @param entity 实体
     * @return 创建后
     */
    MsMonitorGateway create(MsMonitorGateway entity);

    /**
     * 更新
     *
     * @param entity 实体
     * @return 更新后
     */
    MsMonitorGateway update(MsMonitorGateway entity);

    /**
     * 删除
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 按主机查询
     *
     * @param monitorId 主机 id
     * @return 列表
     */
    List<MsMonitorGateway> listByMonitor(Long monitorId);
}