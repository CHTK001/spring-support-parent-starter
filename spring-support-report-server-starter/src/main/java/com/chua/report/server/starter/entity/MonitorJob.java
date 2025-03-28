package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 * 任务
 */
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "任务")
@Schema(description = "任务")
@Data
@TableName(value = "monitor_job")
public class MonitorJob extends SysBase implements Serializable {
    @TableId(value = "job_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer jobId;

    /**
     * 名称
     */
    @TableField(value = "job_name")
    @ApiModelProperty(value = "名称")
    @Schema(description = "名称")
    @Size(max = 255, message = "名称最大长度要小于 255")
    private String jobName;

    /**
     * 任务类型; cron, fixed
     */
    @TableField(value = "job_schedule_type")
    @ApiModelProperty(value = "任务类型; cron, fixed")
    @Schema(description = "任务类型; cron, fixed")
    @Size(max = 255, message = "任务类型; cron, fixed最大长度要小于 255")
    private String jobScheduleType;

    /**
     * 时间配置
     */
    @TableField(value = "job_schedule_time")
    @ApiModelProperty(value = "时间配置")
    @Schema(description = "时间配置")
    @Size(max = 255, message = "时间配置最大长度要小于 255")
    private String jobScheduleTime;
    /**
     * 时间配置
     */
    @TableField(value = "job_author")
    @ApiModelProperty(value = "负责人")
    @Schema(description = "负责人")
    @Size(max = 255, message = "负责人最大长度要小于 255")
    private String jobAuthor;
    /**
     * 时间配置
     */
    @TableField(value = "job_alarm_email")
    @ApiModelProperty(value = "报警邮件")
    @Schema(description = "报警邮件")
    @Size(max = 255, message = "报警邮件最大长度要小于 255")
    private String jobAlarmEmail;



    /**
     * 状态;0: 未启用； 1: 正在运行
     */
    @TableField(value = "job_trigger_status")
    @ApiModelProperty(value = "状态;0: 未启用； 1: 正在运行")
    @Schema(description = "状态;0: 未启用； 1: 正在运行")
    private Integer jobTriggerStatus;

    /**
     * 对应的应用ID
     */
    @TableField(value = "monitor_id")
    @ApiModelProperty(value = "对应的应用ID")
    @Schema(description = "对应的应用ID")
    @Size(max = 255, message = "对应的应用ID最大长度要小于 255")
    private Integer monitorId;
    /**
     * 对应的应用名称
     */
    @TableField(value = "job_application_name")
    @ApiModelProperty(value = "对应的应用名称")
    @Schema(description = "对应的应用名称")
    @Size(max = 255, message = "对应的应用名称最大长度要小于 255")
    private String jobApplicationName;

    /**
     * 环境
     */
    @TableField(value = "job_application_active")
    @ApiModelProperty(value = "环境")
    @Schema(description = "环境")
    @Size(max = 255, message = "环境最大长度要小于 255")
    private String jobApplicationActive;

    /**
     * 描述
     */
    @TableField(value = "job_desc")
    @ApiModelProperty(value = "描述")
    @Schema(description = "描述")
    @Size(max = 255, message = "描述最大长度要小于 255")
    private String jobDesc;

    /**
     * Glue最后更新时间
     */
    @TableField(value = "job_glue_updatetime")
    @ApiModelProperty(value = "Glue最后更新时间")
    @Schema(description = "Glue最后更新时间")
    private Date jobGlueUpdatetime;

    /**
     * 源码
     */
    @TableField(value = "job_glue_source")
    @ApiModelProperty(value = "源码")
    @Schema(description = "源码")
    @Size(max = 255, message = "源码最大长度要小于 255")
    private String jobGlueSource;

    /**
     * Glue类型（例如：脚本、JAR等）
     */
    @TableField(value = "job_glue_type")
    @ApiModelProperty(value = "Glue类型（例如：脚本、JAR等）")
    @Schema(description = "Glue类型（例如：脚本、JAR等）")
    @Size(max = 255, message = "Glue类型（例如：脚本、JAR等）最大长度要小于 255")
    private String jobGlueType;

    /**
     * 失败重试次数
     */
    @TableField(value = "job_fail_retry")
    @ApiModelProperty(value = "失败重试次数")
    @Schema(description = "失败重试次数")
    private Integer jobFailRetry;

    /**
     * 超时时间
     */
    @TableField(value = "job_execute_timeout")
    @ApiModelProperty(value = "超时时间")
    @Schema(description = "超时时间")
    private Integer jobExecuteTimeout;

    /**
     * 模式; spring, xxl-job
     */
    @TableField(value = "job_execute_type")
    @ApiModelProperty(value = "模式; spring, xxl-job")
    @Schema(description = "模式; spring, xxl-job")
    @Size(max = 255, message = "模式; spring, xxl-job最大长度要小于 255")
    private String jobExecuteType;

    /**
     * 失效后策略
     */
    @TableField(value = "job_execute_misfire_strategy")
    @ApiModelProperty(value = "失效后策略")
    @Schema(description = "失效后策略")
    @Size(max = 255, message = "失效后策略最大长度要小于 255")
    private String jobExecuteMisfireStrategy;

    /**
     * bean名称
     */
    @TableField(value = "job_execute_bean")
    @ApiModelProperty(value = "bean名称")
    @Schema(description = "bean名称")
    @Size(max = 255, message = "bean名称最大长度要小于 255")
    private String jobExecuteBean;

    /**
     * 执行参数
     */
    @TableField(value = "job_execute_param")
    @ApiModelProperty(value = "执行参数")
    @Schema(description = "执行参数")
    @Size(max = 255, message = "执行参数最大长度要小于 255")
    private String jobExecuteParam;

    /**
     * 执行路由策略
     */
    @TableField(value = "job_execute_router")
    @ApiModelProperty(value = "执行路由策略")
    @Schema(description = "执行路由策略")
    @Size(max = 255, message = "执行路由策略最大长度要小于 255")
    private String jobExecuteRouter;

    /**
     * 任务触发的最后一次时间
     */
    @TableField(value = "job_trigger_last_time")
    @ApiModelProperty(value = "任务触发的最后一次时间")
    @Schema(description = "任务触发的最后一次时间")
    private Long jobTriggerLastTime;

    /**
     * 任务触发的下一次时间
     */
    @TableField(value = "job_trigger_next_time")
    @ApiModelProperty(value = "任务触发的下一次时间")
    @Schema(description = "任务触发的下一次时间")
    private Long jobTriggerNextTime;



    private static final long serialVersionUID = 1L;
}