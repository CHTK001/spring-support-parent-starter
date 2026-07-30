package com.chua.starter.strategy.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 调度任务配置实体
 * 
 * 用于存储定时任务的配置信息，支持通过页面动态调整。
 * 数据库配置优先级高于注解配置。
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Data
@TableName("sys_scheduler_configuration")
@Schema(description = "调度任务配置")
public class SysSchedulerConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long sysSchedulerId;

    /**
     * 任务名称
     */
    @Schema(description = "任务名称")
    private String sysSchedulerName;

    /**
     * 任务组名
     */
    @Schema(description = "任务组名")
    private String sysSchedulerGroup;

    /**
     * 任务类型
     * cron: Cron表达式
     * fixed_rate: 固定频率
     * fixed_delay: 固定延迟
     */
    @Schema(description = "任务类型：cron-Cron表达式, fixed_rate-固定频率, fixed_delay-固定延迟")
    private String sysSchedulerType;

    /**
     * Cron表达式（当type=cron时使用）
     */
    @Schema(description = "Cron表达式")
    private String sysSchedulerCron;

    /**
     * 固定频率/延迟时间（毫秒）
     */
    @Schema(description = "固定频率/延迟时间（毫秒）")
    private Long sysSchedulerInterval;

    /**
     * 初始延迟时间（毫秒）
     */
    @Schema(description = "初始延迟时间（毫秒）")
    private Long sysSchedulerInitialDelay;

    /**
     * 任务执行Bean名称
     */
    @Schema(description = "任务执行Bean名称")
    private String sysSchedulerBeanName;

    /**
     * 任务执行方法名
     */
    @Schema(description = "任务执行方法名")
    private String sysSchedulerMethodName;

    /**
     * 任务参数（JSON格式）
     */
    @Schema(description = "任务参数（JSON格式）")
    private String sysSchedulerParams;

    /**
     * 是否允许并发执行
     */
    @Schema(description = "是否允许并发执行")
    private Boolean sysConcurrentAllowed;

    /**
     * 状态：0-暂停, 1-运行
     */
    @Schema(description = "状态：0-暂停, 1-运行")
    private Integer sysSchedulerStatus;

    /**
     * 上次执行时间
     */
    @Schema(description = "上次执行时间")
    private LocalDateTime lastExecuteTime;

    /**
     * 下次执行时间
     */
    @Schema(description = "下次执行时间")
    private LocalDateTime nextExecuteTime;

    /**
     * 描述信息
     */
    @Schema(description = "描述信息")
    private String sysSchedulerDescription;

    /**
     * 排序值
     */
    @Schema(description = "排序值")
    private Integer sysSchedulerSort;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建人ID")
    private Long createBy;

    /**
     * 创建人姓名
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建人姓名")
    private String createName;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新人ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新人ID")
    private Long updateBy;

    /**
     * 更新人姓名
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新人姓名")
    private String updateName;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
