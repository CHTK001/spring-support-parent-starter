package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集调度总览行VO（单个任务的调度视图）
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class CollectScheduleTaskVO {

    /**
     * 采集任务标识
     */
    private Long collectTaskId;

    /**
     * 任务名称
     */
    private String collectTaskName;

    /**
     * 来源类型
     */
    private String collectTaskSourceType;

    /**
     * 任务配置的 Cron（空=默认调度）
     */
    private String collectTaskCron;

    /**
     * 实际生效的 Cron
     */
    private String effectiveCron;

    /**
     * 状态（0=禁用, 1=启用）
     */
    private Integer collectTaskStatus;

    /**
     * 下次执行时间
     */
    private LocalDateTime collectTaskNextRunTime;

    /**
     * 最近执行时间
     */
    private LocalDateTime collectTaskLastRunTime;

    /**
     * 最近执行结果（0=失败, 1=成功）
     */
    private Integer collectTaskLastRunStatus;

    /**
     * 最近执行采集题数
     */
    private Integer collectTaskLastRunCount;

    /**
     * 累计采集题数
     */
    private Integer collectTaskTotalCount;
}
