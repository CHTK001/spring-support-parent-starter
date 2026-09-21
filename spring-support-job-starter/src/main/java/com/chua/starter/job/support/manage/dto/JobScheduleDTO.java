package com.chua.starter.job.support.manage.dto;

import lombok.Data;

/**
 * 调度配置改期入参。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class JobScheduleDTO {

    /**
     * 任务键
     */
    private String jobKey;

    /**
     * 调度类型: CRON / FIXED / DELAY / NONE
     */
    private String scheduleType;

    /**
     * 调度配置: cron 表达式或毫秒间隔
     */
    private String scheduleTime;
}
