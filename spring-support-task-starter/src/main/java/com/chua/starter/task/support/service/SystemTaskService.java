package com.chua.starter.task.support.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.task.support.params.TaskStatus;
import com.chua.starter.task.support.pojo.SysTask;

import java.util.List;

/**
 * @author CH
 */
public interface SystemTaskService {

    /**
     * 更新数据集
     *
     * @param taskTid 任务ID
     * @return 结果
     */
    int deleteWithId(String taskTid);

    /**
     * 更新数据集
     *
     * @param task 任务
     * @return 结果
     */
    int updateWithId(SysTask task);

    /**
     * 更新数据集
     *
     * @param task 任务
     * @return 结果
     */
    int forceUpdateWithId(SysTask task);

    /**
     * 更新数据集
     *
     * @param task   任务
     * @param userId 用户ID
     * @return 结果
     */
    boolean save(SysTask task, String userId);

    /**
     * 查询
     *
     * @param page 分页
     * @return 结果
     */
    Page<SysTask> withPage(Page<SysTask> page);

    /**
     * 更新數據状态
     *
     * @param taskId 任務ID
     * @param status 狀態
     */
    void forUpdate(String taskId, int status);

    /**
     * 更新数据步长
     *
     * @param taskId 任务
     */
    void forUpdateCurrent(SysTask task);

    /**
     * 任务ID
     *
     * @param taskTid 任务ID
     */
    SysTask getTaskByTaskTid(String taskTid);

    /**
     * 获取当前进度
     *
     * @param taskTid 任务ID
     * @return 进度
     */
    TaskStatus getTaskByTaskStatus(String taskTid);

    /**
     * 查询任务
     *
     * @param wrapper 条件
     * @return 结果
     */
    List<SysTask> list(LambdaQueryWrapper<SysTask> wrapper);

    /**
     * 重置
     *
     * @param taskTid ID
     */
    void reset(String taskTid);
}
