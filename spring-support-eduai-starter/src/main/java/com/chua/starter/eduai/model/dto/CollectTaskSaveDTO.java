package com.chua.starter.eduai.model.dto;

import lombok.Data;

/**
 * 采集任务保存入参。
 *
 * <p>只收业务列 + cron：计量列（累计/最近执行）与归属人由服务端维护，不接受客户端提交；
 * cron 不在本表落列，随任务一起登记到 job-starter 的调度任务上。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class CollectTaskSaveDTO {

    /**
     * 任务标识 (为空表示新增)
     */
    private Long collectTaskId;

    /**
     * 任务名称
     */
    private String collectTaskName;

    /**
     * 来源类型 (SPI Provider type)
     */
    private String collectTaskSourceType;

    /**
     * 来源配置 (JSON, GROOVY 任务含 script/scriptPath)
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
     * 难度 (1-5)
     */
    private Integer collectTaskDifficulty;

    /**
     * 状态 (0=禁用, 1=启用), 为空按启用
     */
    private Integer collectTaskStatus;

    /**
     * 备注
     */
    private String collectTaskRemark;

    /**
     * 调度 cron (6 段, 留空使用默认周期)
     */
    private String cron;
}
