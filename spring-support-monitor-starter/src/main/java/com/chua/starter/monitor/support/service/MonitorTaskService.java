package com.chua.starter.monitor.support.service;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsMonitorTask;

import java.util.List;

/**
 * 监控任务 服务
 *
 * @since 2026/8/4
 * @author CH
 */
public interface MonitorTaskService {

    /**
     * 创建任务
     *
     * @param task 任务实体
     * @return 创建结果
     */
    ReturnResult<MsMonitorTask> create(MsMonitorTask task);

    /**
     * 更新任务
     *
     * @param task 任务实体
     * @return 更新结果
     */
    ReturnResult<MsMonitorTask> update(MsMonitorTask task);

    /**
     * 删除任务
     *
     * @param id 主键
     * @return 删除结果
     */
    ReturnResult<Void> delete(Long id);

    /**
     * 按 标识 查询
     *
     * @param id 主键
     * @return 查询结果
     */
    ReturnResult<MsMonitorTask> findById(Long id);

    /**
     * 查询启用的任务列表
     *
     * @return 任务列表
     */
    ReturnResult<List<MsMonitorTask>> listEnabled();

    /**
     * 按监控主机查询任务
     *
     * @param monitorId 主机 标识
     * @return 任务列表
     */
    ReturnResult<List<MsMonitorTask>> listByMonitor(Long monitorId);

    /**
     * 设置启用/禁用
     *
     * @param id      主键
     * @param enabled 启用状态
     * @return 操作结果
     */
    ReturnResult<Void> setEnabled(Long id, Integer enabled);

    /**
     * 标记上次运行时间
     *
     * @param id 主键
     * @return 操作结果
     */
    ReturnResult<Void> markLastRunTime(Long id);
}
