package com.chua.starter.monitor.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 任务
 */
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
    @TableField(value = "job_type")
    @ApiModelProperty(value = "任务类型; cron, fixed")
    @Schema(description = "任务类型; cron, fixed")
    @Size(max = 255, message = "任务类型; cron, fixed最大长度要小于 255")
    private String jobType;

    /**
     * 时间配置
     */
    @TableField(value = "job_conf")
    @ApiModelProperty(value = "时间配置")
    @Schema(description = "时间配置")
    @Size(max = 255, message = "时间配置最大长度要小于 255")
    private String jobConf;

    /**
     * 状态;0: 未启用； 1: 正在运行
     */
    @TableField(value = "job_status")
    @ApiModelProperty(value = "状态;0: 未启用； 1: 正在运行")
    @Schema(description = "状态;0: 未启用； 1: 正在运行")
    private Integer jobStatus;

    /**
     * 对应的应用ID
     */
    @TableField(value = "job_app")
    @ApiModelProperty(value = "对应的应用ID")
    @Schema(description = "对应的应用ID")
    @Size(max = 255, message = "对应的应用ID最大长度要小于 255")
    private String jobApp;

    /**
     * 环境
     */
    @TableField(value = "job_profile")
    @ApiModelProperty(value = "环境")
    @Schema(description = "环境")
    @Size(max = 255, message = "环境最大长度要小于 255")
    private String jobProfile;

    /**
     * 模式; spring, xxl-job
     */
    @TableField(value = "job_execute_type")
    @ApiModelProperty(value = "模式; spring, xxl-job")
    @Schema(description = "模式; spring, xxl-job")
    @Size(max = 255, message = "模式; spring, xxl-job最大长度要小于 255")
    private String jobExecuteType;

    /**
     * bean名称
     */
    @TableField(value = "job_execute_bean")
    @ApiModelProperty(value = "bean名称")
    @Schema(description = "bean名称")
    @Size(max = 255, message = "bean名称最大长度要小于 255")
    private String jobExecuteBean;

    /**
     * 执行路由策略
     */
    @TableField(value = "job_execute_route")
    @ApiModelProperty(value = "执行路由策略")
    @Schema(description = "执行路由策略")
    @Size(max = 255, message = "执行路由策略最大长度要小于 255")
    private String jobExecuteRoute;

    /**
     * 描述
     */
    @TableField(value = "job_desc")
    @ApiModelProperty(value = "描述")
    @Schema(description = "描述")
    @Size(max = 255, message = "描述最大长度要小于 255")
    private String jobDesc;

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