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
 * 任务执行日志实体
 * <p>
 * 对应数据库表 sys_job_log，记录每次任务执行的信息。
 * 包括触发时间、执行状态、耗时、执行结果等。
 * </p>
 *
 * <h3>状态说明</h3>
 * <ul>
 *     <li><b>PADDING</b> - 执行中，任务已触发正在执行</li>
 *     <li><b>SUCCESS</b> - 执行成功</li>
 *     <li><b>FAILURE</b> - 执行失败</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 * @see SysJob
 * @see SysJobLogDetail
 */
@Data
@TableName(value = "sys_job_log")
public class SysJobLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    @TableId(value = "job_log_id", type = IdType.AUTO)
    private Integer jobLogId;

    /**
     * 日志编号。
     * <p>
     * 文件名、跨系统回执和页面展示优先使用该编号，避免直接暴露自增主键。
     * </p>
     */
    @TableField(value = "job_log_no")
    private String jobLogNo;

    /**
     * 关联任务ID。
     */
    @TableField(value = "job_id")
    private Integer jobId;

    /**
     * 关联任务编号。
     */
    @TableField(value = "job_no")
    private String jobNo;

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
     * 触发时所在环境/Profile。
     * <p>
     * 该字段在较新的任务日志表结构里已经存在，但历史实体类遗漏了映射，
     * 会导致 JdbcEngine 无法在 UPDATE 模式下补齐该列。
     * </p>
     */
    @TableField(value = "job_log_profile")
    private String jobLogProfile;

    /**
     * 触发来源地址。
     * <p>
     * 调度中心在远程执行模式下会把命中的执行器地址写入该列，便于平台排查
     * “任务是由哪个远程执行器接单”的问题。
     * </p>
     */
    @TableField(value = "job_log_trigger_address")
    private String jobLogTriggerAddress;

    /**
     * 对应的日志文件路径。
     */
    @TableField(value = "job_log_file_path")
    private String jobLogFilePath;

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
