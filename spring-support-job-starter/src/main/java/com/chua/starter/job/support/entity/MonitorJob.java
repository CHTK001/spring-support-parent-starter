package com.chua.starter.job.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 任务
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "任务")
@Schema(description = "任务")
@Data
@TableName(value = "monitor_job")
public class MonitorJob extends SysBase {

    @TableId(value = "job_id", type = IdType.AUTO)
    @ApiModelProperty(value = "任务ID")
    @Schema(description = "任务ID")
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
     * 负责人
     */
    @TableField(value = "job_author")
    @ApiModelProperty(value = "负责人")
    @Schema(description = "负责人")
    @Size(max = 255, message = "负责人最大长度要小于 255")
    private String jobAuthor;

    /**
     * 报警邮件
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
     * 失效后策略
     */
    @TableField(value = "job_execute_misfire_strategy")
    @ApiModelProperty(value = "失效后策略")
    @Schema(description = "失效后策略")
    @Size(max = 255, message = "失效后策略最大长度要小于 255")
    private String jobExecuteMisfireStrategy;

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
