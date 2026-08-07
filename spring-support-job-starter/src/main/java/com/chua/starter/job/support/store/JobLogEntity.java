package com.chua.starter.job.support.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * sys_job_log 任务执行日志实体。
 *
 * <p>字段以表名 {@code sys_job_log} 作为前缀; 审计字段来自 {@link SysBase}。
 * 跨数据库 SQL 见 {@code V1.0__init_job.sql}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job_log")
public class JobLogEntity extends SysBase implements Serializable {

    /**
     * 序列化版本
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志 ID
     */
    @TableId(value = "sys_job_log_id", type = IdType.AUTO)
    private Long sysJobLogId;

    /**
     * 日志编号 (业务唯一)
     */
    @TableField("sys_job_log_no")
    private String sysJobLogNo;

    /**
     * 任务 ID
     */
    @TableField("sys_job_id")
    private Long sysJobId;

    /**
     * 任务编号
     */
    @TableField("sys_job_no")
    private String sysJobNo;

    /**
     * 触发应用 (local / executor / remote)
     */
    @TableField("sys_job_log_app")
    private String sysJobLogApp;

    /**
     * handler 名称
     */
    @TableField("sys_job_log_trigger_bean")
    private String sysJobLogTriggerBean;

    /**
     * 触发类型 (CRON / MANUAL / API / RETRY / MISFIRE)
     */
    @TableField("sys_job_log_trigger_type")
    private String sysJobLogTriggerType;

    /**
     * 触发时所属 profile
     */
    @TableField("sys_job_log_profile")
    private String sysJobLogProfile;

    /**
     * 触发时间
     */
    @TableField("sys_job_log_trigger_time")
    private Date sysJobLogTriggerTime;

    /**
     * 触发日期
     */
    @TableField("sys_job_log_trigger_date")
    private Date sysJobLogTriggerDate;

    /**
     * 触发状态码
     */
    @TableField("sys_job_log_trigger_code")
    private String sysJobLogTriggerCode;

    /**
     * 执行状态码 (PADDING / SUCCESS / FAILURE / SKIPPED)
     */
    @TableField("sys_job_log_execute_code")
    private String sysJobLogExecuteCode;

    /**
     * 执行耗时 (毫秒)
     */
    @TableField("sys_job_log_cost")
    private BigDecimal sysJobLogCost;

    /**
     * 触发消息
     */
    @TableField("sys_job_log_trigger_msg")
    private String sysJobLogTriggerMsg;

    /**
     * 触发参数
     */
    @TableField("sys_job_log_trigger_param")
    private String sysJobLogTriggerParam;

    /**
     * 触发地址
     */
    @TableField("sys_job_log_trigger_address")
    private String sysJobLogTriggerAddress;

    /**
     * 来源节点 ID
     */
    @TableField("sys_job_log_node_id")
    private String sysJobLogNodeId;

    /**
     * 日志文件路径
     */
    @TableField("sys_job_log_file_path")
    private String sysJobLogFilePath;

    /**
     * 逻辑删除标记
     */
    @TableField("sys_job_log_is_deleted")
    private Integer sysJobLogIsDeleted;
}