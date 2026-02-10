package com.chua.starter.sync.data.support.service.sync;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.sync.data.support.entity.MonitorSyncConnection;
import com.chua.starter.sync.data.support.entity.MonitorSyncNode;
import com.chua.starter.sync.data.support.entity.MonitorSyncTask;
import com.chua.starter.sync.data.support.entity.MonitorSyncTaskLog;
import com.chua.starter.sync.data.support.sync.SyncTaskDesign;
import com.chua.starter.sync.data.support.sync.SyncTaskQuery;
import com.chua.starter.sync.data.support.sync.SyncTaskStatistics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 同步任务服务接口
 *
 * @author CH
 * @since 2024/12/19
 */
public interface MonitorSyncTaskService extends IService<MonitorSyncTask> {

    /**
     * 分页查询同步任务列表
     *
     * @param query 查询条件
     * @return 任务分页数据
     */
    ReturnResult<Page<MonitorSyncTask>> pageList(SyncTaskQuery query);

    /**
     * 创建同步任务
     *
     * @param task 任务信息
     * @return 创建结果
     */
    ReturnResult<MonitorSyncTask> createTask(MonitorSyncTask task);

    /**
     * 更新同步任务基本信息
     *
     * @param task 任务信息
     * @return 更新结果
     */
    ReturnResult<Boolean> updateTask(MonitorSyncTask task);

    /**
     * 删除同步任务(包括节点、连线、日志)
     *
     * @param taskId 任务ID
     * @return 删除结果
     */
    ReturnResult<Boolean> deleteTask(Long taskId);

    /**
     * 获取任务详情(包括节点和连线)
     *
     * @param taskId 任务ID
     * @return 任务设计数据
     */
    ReturnResult<SyncTaskDesign> getTaskDesign(Long taskId);

    /**
     * 保存任务设计(节点和连线)
     *
     * @param taskId 任务ID
     * @param design 设计数据
     * @return 保存结果
     */
    ReturnResult<Boolean> saveTaskDesign(Long taskId, SyncTaskDesign design);

    /**
     * 启动同步任务
     *
     * @param taskId 任务ID
     * @return 启动结果
     */
    ReturnResult<Boolean> startTask(Long taskId);

    /**
     * 停止同步任务
     *
     * @param taskId 任务ID
     * @return 停止结果
     */
    ReturnResult<Boolean> stopTask(Long taskId);

    /**
     * 手动执行一次同步任务
     *
     * @param taskId 任务ID
     * @return 执行结果
     */
    ReturnResult<Long> executeOnce(Long taskId);

    /**
     * 获取任务执行日志列表
     *
     * @param taskId 任务ID
     * @param page 页码
     * @param size 每页大小
     * @return 日志分页数据
     */
    ReturnResult<Page<MonitorSyncTaskLog>> getTaskLogs(Long taskId, Integer page, Integer size);

    /**
     * 获取单条日志详情
     *
     * @param logId 日志ID
     * @return 日志详情
     */
    ReturnResult<MonitorSyncTaskLog> getLogDetail(Long logId);

    /**
     * 验证任务设计是否有效
     *
     * @param design 设计数据
     * @return 验证结果
     */
    ReturnResult<Boolean> validateDesign(SyncTaskDesign design);

    /**
     * 复制任务
     *
     * @param taskId 原任务ID
     * @param newName 新任务名称
     * @return 新任务
     */
    ReturnResult<MonitorSyncTask> copyTask(Long taskId, String newName);

    /**
     * 获取任务节点列表
     *
     * @param taskId 任务ID
     * @return 节点列表
     */
    ReturnResult<List<MonitorSyncNode>> getTaskNodes(Long taskId);

    /**
     * 获取任务连线列表
     *
     * @param taskId 任务ID
     * @return 连线列表
     */
    ReturnResult<List<MonitorSyncConnection>> getTaskConnections(Long taskId);

    /**
     * 获取执行统计数据(全局)
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param granularity 粒度: hour/day
     * @return 统计数据
     */
    ReturnResult<SyncTaskStatistics> getStatistics(LocalDateTime startTime, LocalDateTime endTime, String granularity);

    /**
     * 获取指定任务的执行统计数据
     *
     * @param taskId 任务ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param granularity 粒度: hour/day
     * @return 统计数据
     */
    ReturnResult<SyncTaskStatistics> getTaskStatistics(Long taskId, LocalDateTime startTime, LocalDateTime endTime, String granularity);
}
