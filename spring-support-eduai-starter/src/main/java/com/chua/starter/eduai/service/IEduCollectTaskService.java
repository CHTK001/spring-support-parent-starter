package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduCollectLog;
import com.chua.starter.eduai.model.EduCollectTask;
import com.chua.starter.eduai.model.dto.CollectTaskQueryDTO;
import com.chua.starter.eduai.model.vo.CollectScheduleOverviewVO;
import com.chua.starter.eduai.model.vo.CollectTriggerVO;

import java.util.List;

/**
 * 题库采集任务服务
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduCollectTaskService extends IService<EduCollectTask> {

    /**
     * 分页查询采集任务
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<EduCollectTask> pageQuery(CollectTaskQueryDTO query);

    /**
     * 新增或更新采集任务（校验 cron 与来源类型，重算下次执行时间）
     *
     * @param task 任务数据
     * @return 任务ID
     */
    Long saveTask(EduCollectTask task);

    /**
     * 删除采集任务
     *
     * @param id 任务ID
     * @return 是否成功
     */
    boolean deleteTask(Long id);

    /**
     * 启用/禁用任务（启用时重算下次执行时间）
     *
     * @param id 任务ID
     * @param status 0-禁用 1-启用
     * @return 是否成功
     */
    boolean changeStatus(Long id, Integer status);

    /**
     * 手动触发一次采集（同步执行）
     *
     * @param id 任务ID
     * @return 触发结果（status: success|fail|skip）
     */
    CollectTriggerVO trigger(Long id);

    /**
     * 分页查询采集执行日志
     *
     * @param taskId 任务ID（可空）
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<EduCollectLog> pageLogs(Long taskId, Integer pageNum, Integer pageSize);

    /**
     * 调度总览：每个启用任务的 cron / 下次执行时间 / 最近结果
     *
     * @return 总览数据
     */
    CollectScheduleOverviewVO scheduleOverview();

    /**
     * 调度器入口：执行全部到期任务
     */
    void dispatchDueTasks();

    /**
     * 可用来源类型清单（SPI 注册表）
     *
     * @return 类型列表
     */
    List<String> availableSourceTypes();
}
