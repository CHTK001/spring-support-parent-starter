package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduCollectLog;
import com.chua.starter.eduai.model.EduCollectTask;
import com.chua.starter.eduai.model.dto.CollectTaskQueryDTO;
import com.chua.starter.eduai.model.dto.CollectTaskSaveDTO;
import com.chua.starter.eduai.model.vo.CollectMetaVO;
import com.chua.starter.eduai.model.vo.CollectScriptSampleVO;
import com.chua.starter.eduai.model.vo.CollectTaskVO;
import com.chua.starter.eduai.model.vo.CollectTriggerVO;

import java.util.List;

/**
 * 题库采集任务服务。
 *
 * <p>一个采集任务 = 一段 Groovy/数据源配置 + 一个 job-starter 调度任务：
 * 业务归属、脚本与题目口径留在 edu_collect_task，cron、启停与派发时刻由
 * {@code JobOperator} 收口到 sys_job，页面上的"下次执行时间"与调度器用的是同一份数据。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduCollectTaskService extends IService<EduCollectTask> {

    /**
     * 分页查询采集任务（含调度态：cron / 下次触发 / 最近派发）
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<CollectTaskVO> pageQuery(CollectTaskQueryDTO query);

    /**
     * 新增或更新采集任务（同步登记/更新对应的调度任务）
     *
     * @param dto 任务数据 (含 cron)
     * @return 任务ID
     */
    Long saveTask(CollectTaskSaveDTO dto);

    /**
     * 删除采集任务（连同其调度任务与执行器一起摘除）
     *
     * @param id 任务ID
     * @return 是否成功
     */
    boolean deleteTask(Long id);

    /**
     * 启用/禁用任务（同步切换 sys_job 触发状态，停用后不再自动派发）
     *
     * @param id 任务ID
     * @param status 0-禁用 1-启用
     * @return 是否成功
     */
    boolean changeStatus(Long id, Integer status);

    /**
     * 手动触发一次采集（同步执行，任务未启用时返回 skip）
     *
     * @param id 任务ID
     * @return 触发结果（status: success|fail|skip）
     */
    CollectTriggerVO trigger(Long id);

    /**
     * 调度入口：按任务标识执行一次自动采集（由 edu-collect-&lt;id&gt; 处理器回调）
     *
     * @param id 任务ID
     */
    void runScheduled(Long id);

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
     * 采集页元数据（服务器时刻 + 默认 cron）
     *
     * @return 元数据
     */
    CollectMetaVO collectMeta();

    /**
     * 启动期对账：为每个采集任务注册执行器并补齐缺失的调度任务行
     *
     * @return 对账的任务数
     */
    int restoreScheduling();

    /**
     * 可用来源类型清单（SPI 注册表）
     *
     * @return 类型列表
     */
    List<String> availableSourceTypes();

    /**
     * 内置 Groovy 采集脚本样例（供采集页一键插入）
     *
     * @return 样例列表
     */
    List<CollectScriptSampleVO> scriptSamples();
}
