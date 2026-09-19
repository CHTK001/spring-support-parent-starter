package com.chua.starter.monitor.support.model;

/**
 * Agent 采集任务视图
 *
 * @param taskName 任务名
 * @param taskType 任务类型（服务对象类型）
 * @param taskCron 采集 表达式
 * @author CH
 * @since 4.0.0.42
 */
public record AgentTaskVO(String taskName, String taskType, String taskCron) {
}
