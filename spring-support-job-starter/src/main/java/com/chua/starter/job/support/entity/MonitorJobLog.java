package com.chua.starter.job.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 任务日志
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 */
@Data
@TableName(value = "monitor_job_log")
public class MonitorJobLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    @TableId(value = "job_log_id", type = IdType.AUTO)
    private Integer jobLogId;

    /**
     * 触发时间
     */
    @TableField(value = "job_log_trigger_time")
    private Date jobLogTriggerTime;

    /**
     * 触发日期
     */
    @TableField(value = "job_log_trigger_date")
    private LocalDate jobLogTriggerDate;

    /**
     * 触发状态
     */
    @TableField(value = "job_log_trigger_code")
    private String jobLogTriggerCode;

    /**
     * 执行状态; FAILURE: 失败; SUCCESS: 成功; PADDING: 执行中
     */
    @TableField(value = "job_log_execute_code")
    private String jobLogExecuteCode;

    /**
     * 执行对应的标识
     */
    @TableField(value = "job_log_trigger_bean")
    private String jobLogTriggerBean;

    /**
     * 耗时
     */
    @TableField(value = "job_log_cost")
    private BigDecimal jobLogCost;

    /**
     * 触发消息
     */
    @TableField(value = "job_log_trigger_msg")
    private String jobLogTriggerMsg;

    /**
     * 触发应用
     */
    @TableField(value = "job_log_app")
    private String jobLogApp;

    /**
     * 触发参数
     */
    @TableField(value = "job_log_trigger_param")
    private String jobLogTriggerParam;

    /**
     * 触发类型
     */
    @TableField(value = "job_log_trigger_type")
    private String jobLogTriggerType;

    /**
     * 触发时间
     */
    @TableField(exist = false)
    private LocalDateTime startDate;

    /**
     * 结束时间
     */
    @TableField(exist = false)
    private LocalDateTime endDate;
}
