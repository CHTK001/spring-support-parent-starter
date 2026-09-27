package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集任务列表出参：业务归属列（edu_collect_task）+ 调度态（sys_job）。
 *
 * <p>cron、下次/上次派发时刻不在本表落列，全部来自 job-starter 的任务运行态快照，
 * 因此"改了时间"与"页面上看到的下次执行"必然同源。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class CollectTaskVO {

    /**
     * 采集任务标识
     */
    private Long collectTaskId;

    /**
     * 任务名称
     */
    private String collectTaskName;

    /**
     * 来源类型（SPI Provider type）
     */
    private String collectTaskSourceType;

    /**
     * 来源配置（JSON，GROOVY 任务含 script/scriptPath）
     */
    private String collectTaskSourceConfig;

    /**
     * 学科
     */
    private String collectTaskSubject;

    /**
     * 学段
     */
    private String collectTaskGradeLevel;

    /**
     * 年级
     */
    private String collectTaskGrade;

    /**
     * 难度（1-5）
     */
    private Integer collectTaskDifficulty;

    /**
     * 状态（0=停用, 1=调度中）：调度行已登记时取 sys_job 的触发状态，未登记时回落业务列
     */
    private Integer collectTaskStatus;

    /**
     * 备注
     */
    private String collectTaskRemark;

    /**
     * 累计采集题数
     */
    private Integer collectTaskTotalCount;

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
     * 对应的调度任务键（sys_job 派发标识）
     */
    private String jobKey;

    /**
     * 调度 cron（来自 sys_job，空表示无任务行）
     */
    private String cron;

    /**
     * 下次触发时刻（毫秒时间戳，空表示未排期）
     */
    private Long nextFireTime;

    /**
     * 最近一次派发时刻（毫秒时间戳，空表示从未派发）
     */
    private Long lastFireTime;

    /**
     * 本节点是否已注册该任务的执行器
     */
    private boolean handlerRegistered;
}
