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
import org.hibernate.annotations.ColumnDefault;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 任务日志
 */
@EqualsAndHashCode(callSuper = true)
@ApiModel(description="任务日志")
@Schema(description="任务日志")
@Data
@TableName(value = "monitor_job_log")
public class MonitorJobLog extends SysBase implements Serializable {
    @TableId(value = "job_log_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "不能为null")
    private Integer jobLogId;

    /**
     * 触发时间
     */
    @TableField(value = "job_log_trigger_time")
    @ApiModelProperty(value="触发时间")
    @Schema(description="触发时间")
    private Date jobLogTriggerTime;

    /**
     * 触发时间
     */
    @TableField(value = "job_log_trigger_date")
    @ApiModelProperty(value="触发时间")
    @Schema(description="触发时间")
    private LocalDate jobLogTriggerDate;

    /**
     * 触发状态
     */
    @TableField(value = "job_log_trigger_code")
    @ApiModelProperty(value="触发状态")
    @Schema(description="触发状态")
    private String jobLogTriggerCode;

    /**
     * 触发状态
     */
    @TableField(value = "job_log_execute_code")
    @ApiModelProperty(value="客户端执行任务的结果; FAILURE: 失败; SUCCESS: 成功; PADDING: 执行中")
    @Schema(description="客户端执行任务的结果; FAILURE: 失败; SUCCESS: 成功; PADDING: 执行中")
    @ColumnDefault("PADDING")
    private String jobLogExecuteCode;
    /**
     * 执行对应的标识
     */
    @TableField(value = "job_log_trigger_bean")
    @ApiModelProperty(value="执行对应的标识")
    @Schema(description="执行对应的标识")
    private String jobLogTriggerBean;

    /**
     * 耗时
     */
    @TableField(value = "job_log_cost")
    @ApiModelProperty(value="耗时")
    @Schema(description="耗时")
    private BigDecimal jobLogCost;

    /**
     * 触发消息
     */
    @TableField(value = "job_log_trigger_msg")
    @ApiModelProperty(value="触发消息")
    @Schema(description="触发消息")
    @Size(max = 255,message = "触发消息最大长度要小于 255")
    private String jobLogTriggerMsg;

    /**
     * 触发应用
     */
    @TableField(value = "job_log_app")
    @ApiModelProperty(value="触发应用")
    @Schema(description="触发应用")
    @Size(max = 255,message = "触发应用最大长度要小于 255")
    private String jobLogApp;

    /**
     * 触发地址
     */
    @TableField(value = "job_log_trigger_address")
    @ApiModelProperty(value="触发地址")
    @Schema(description="触发地址")
    @Size(max = 255,message = "触发地址最大长度要小于 255")
    private String jobLogTriggerAddress;

    /**
     * 触发参数
     */
    @TableField(value = "job_log_trigger_param")
    @ApiModelProperty(value="触发参数")
    @Schema(description="触发参数")
    @Size(max = 255,message = "触发参数最大长度要小于 255")
    private String jobLogTriggerParam;

    /**
     * 环境
     */
    @TableField(value = "job_log_profile")
    @ApiModelProperty(value="环境")
    @Schema(description="环境")
    @Size(max = 255,message = "环境最大长度要小于 255")
    private String jobLogProfile;
    /**
     * 触发了类型
     */
    @TableField(value = "job_log_trigger_type")
    @ApiModelProperty(value="触发类型")
    @Schema(description="触发类型")
    @Size(max = 255,message = "触发类型最大长度要小于 255")
    private String jobLogTriggerType;

    @TableField(exist = false)
    private LocalDateTime startDate;

    @TableField(exist = false)
    private LocalDateTime endDate;

    /**
     * 清理类型
     */
    @TableField(exist = false)
    private Integer clearType;

    private static final long serialVersionUID = 1L;
}