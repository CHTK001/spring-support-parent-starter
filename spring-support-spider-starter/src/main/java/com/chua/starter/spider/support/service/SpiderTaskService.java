package com.chua.starter.spider.support.service;

import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.service.dto.CreateTaskResult;

/**
 * 爬虫任务管理服务接口。
 *
 * @author CH
 */
public interface SpiderTaskService {

    /**
     * 创建新任务：自动生成唯一编码，注入默认 START/END 编排，初始状态为 DRAFT。
     *
     * @return 创建结果，包含任务 ID 和默认编排
     */
    CreateTaskResult createTask();

    /**
     * 保存任务（含编排）：执行必填字段校验、编码唯一性校验、凭证安全校验、编排合法性校验，通过后原子性持久化。
     *
     * @param task 任务定义
     * @param flow 编排定义
     * @throws IllegalArgumentException 必填字段为空或编码重复时
     * @throws IllegalStateException    凭证安全校验或编排校验不通过时
     */
    void saveTask(SpiderTaskDefinition task, SpiderFlowDefinition flow);

    /**
     * 删除任务：若为 SCHEDULED 类型则同步删除 job-starter 调度，并清理关联数据。
     *
     * @param taskId 任务 ID
     * @throws IllegalArgumentException 任务不存在时
     */
    void deleteTask(Long taskId);
}
