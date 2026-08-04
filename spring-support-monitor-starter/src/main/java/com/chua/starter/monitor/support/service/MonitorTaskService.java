package com.chua.starter.monitor.support.service;

import com.chua.starter.monitor.support.entity.MsMonitorTask;

import java.util.List;

/**
 * 监控任务 Service
 * <p>
 * 提供任务增删查改与启停控制, 启用缓存降低数据库压力。
 * </p>
 *
 * @author CH
 * @since 2026/8/4
 */
public interface MonitorTaskService {

    /**
     * 创建任务
     *
     * @param task 任务实体
     * @return 创建后的实体(包含 id)
     */
    MsMonitorTask create(MsMonitorTask task);

    /**
     * 更新任务
     *
     * @param task 任务实体
     * @return 更新后的实体
     */
    MsMonitorTask update(MsMonitorTask task);

    /**
     * 删除任务
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 查询单个任务
     *
     * @param id 主键
     * @return 任务实体
     */
    MsMonitorTask findById(Long id);

    /**
     * 查询所有启用的任务
     *
     * @return 任务列表
     */
    List<MsMonitorTask> listEnabled();

    /**
     * 按主机查询任务
     *
     * @param monitorId 主机 id
     * @return 任务列表
     */
    List<MsMonitorTask> listByMonitor(Long monitorId);

    /**
     * 启用/禁用
     *
     * @param id 主键
     * @param enabled 1启用 0禁用
     */
    void setEnabled(Long id, Integer enabled);

    /**
     * 记录最近一次执行时间
     *
     * @param id 任务 id
     */
    void markLastRunTime(Long id);
}