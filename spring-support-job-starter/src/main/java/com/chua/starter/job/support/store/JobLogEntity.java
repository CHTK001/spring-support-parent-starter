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
* sys_作业_日志 任务执行日志实体。
*
* <p>字段以表名 {@code sys_job_log} 作为前缀; 审计字段来自 {@link SysBase}。
* 跨数据库 SQL 见 {@code V1.0__init_job.sql}。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job_log")
public class JobLogEntity extends SysBase implements Serializable {

    /**
    * 序列化版本
     */
    @Serial
    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
    * 日志 标识
     */
    @TableId(value = "sys_job_log_id", type = IdType.ASSIGN_ID)
    /** SYS作业日志标识 */
    private Long sysJobLogId;

    /**
    * 日志编号 (业务唯一)
     */
    @TableField("sys_job_log_no")
    /** SYS作业日志NO */
    private String sysJobLogNo;

    /**
    * 任务 标识
     */
    @TableField("sys_job_id")
    /** SYS作业标识 */
    private Long sysJobId;

    /**
    * 任务编号
     */
    @TableField("sys_job_no")
    /** SYS作业NO */
    private String sysJobNo;

    /**
    * 触发应用 (本地 / 执行器 / 远程)
     */
    @TableField("sys_job_log_app")
    /** SYS作业日志APP */
    private String sysJobLogApp;

    /**
    * 处理器 名称
     */
    @TableField("sys_job_log_trigger_bean")
    /** SYS作业日志triggerbean */
    private String sysJobLogTriggerBean;

    /**
    * 触发类型 (CRON / MANUAL / API / 重试 / MISFIRE)
     */
    @TableField("sys_job_log_trigger_type")
    /** SYS作业日志trigger类型 */
    private String sysJobLogTriggerType;

    /**
    * 触发时所属 配置文件
     */
    @TableField("sys_job_log_profile")
    /** SYS作业日志配置文件 */
    private String sysJobLogProfile;

    /**
    * 触发时间
     */
    @TableField("sys_job_log_trigger_time")
    /** SYS作业日志trigger时间 */
    private Date sysJobLogTriggerTime;

    /**
    * 触发日期
     */
    @TableField("sys_job_log_trigger_date")
    /** SYS作业日志trigger日期 */
    private Date sysJobLogTriggerDate;

    /**
    * 触发状态码
     */
    @TableField("sys_job_log_trigger_code")
    /** SYS作业日志trigger代码 */
    private String sysJobLogTriggerCode;

    /**
    * 执行状态码 (PADDING / 成功 / 失败 / SKIPPED)
     */
    @TableField("sys_job_log_execute_code")
    /** SYS作业日志执行代码 */
    private String sysJobLogExecuteCode;

    /**
    * 执行耗时 (毫秒)
     */
    @TableField("sys_job_log_cost")
    /** SYS作业日志cost */
    private BigDecimal sysJobLogCost;

    /**
    * 触发消息
     */
    @TableField("sys_job_log_trigger_msg")
    /** SYS作业日志triggermsg */
    private String sysJobLogTriggerMsg;

    /**
    * 触发参数
     */
    @TableField("sys_job_log_trigger_param")
    /** SYS作业日志trigger参数 */
    private String sysJobLogTriggerParam;

    /**
    * 触发地址
     */
    @TableField("sys_job_log_trigger_address")
    /** SYS作业日志trigger地址 */
    private String sysJobLogTriggerAddress;

    /**
    * 来源节点 标识
     */
    @TableField("sys_job_log_node_id")
    /** SYS作业日志节点标识 */
    private String sysJobLogNodeId;

    /**
    * 日志文件路径
     */
    @TableField("sys_job_log_file_path")
    /** SYS作业日志文件路径 */
    private String sysJobLogFilePath;

    /**
    * 逻辑删除标记
     */
    @TableField("sys_job_log_is_deleted")
    /** SYS作业日志是否删除标记 */
    private Integer sysJobLogIsDeleted;
}