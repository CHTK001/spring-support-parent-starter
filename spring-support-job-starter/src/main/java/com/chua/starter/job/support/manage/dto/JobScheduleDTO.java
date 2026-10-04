package com.chua.starter.job.support.manage.dto;

/**
 * 调度配置改期入参。
 *
 * @param jobKey       任务键
 * @param scheduleType 调度类型: CRON / FIXED / DELAY / NONE
 * @param scheduleTime 调度配置: cron 表达式或毫秒间隔
 * @since 4.0.0.42
 * @author CH
 */
public record JobScheduleDTO(
        String jobKey,
        String scheduleType,
        String scheduleTime) {
}
