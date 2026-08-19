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

/**
 * sys_job 任务主表实体 (MyBatis-Plus 持久化模型)。
 *
 * <p>所有字段以表名 {@code sys_job} 作为前缀 ({@code sys_job_*});
 * 通用审计字段来自 {@link SysBase} (create_time / update_time / create_by / update_by / create_name / update_name)。
 * 本表额外包含 {@link #sysJobIsDeleted} 与 {@link #sysJobVersion} 用于逻辑删除与乐观锁。</p>
 *
 * <p>跨数据库导入要求见同目录 {@code V1.0__init_job.sql},字段命名仅使用 ASCII,
 * 不使用方言专属语法,目标库: MySQL 8+ / PostgreSQL 12+ / Oracle 12c+。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job")
public class JobEntity extends SysBase implements Serializable {

    /**
     * 序列化版本
     */
    @Serial
    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 任务 ID (主键)
     */
    @TableId(value = "sys_job_id", type = IdType.ASSIGN_ID)
    /** SYS作业ID */
    private Long sysJobId;

    /**
     * 业务编号 (非主键, 对外唯一)
     */
    @TableField("sys_job_no")
    /** SYS作业NO */
    private String sysJobNo;

    /**
     * 任务名
     */
    @TableField("sys_job_name")
    /** SYS作业名称 */
    private String sysJobName;

    /**
     * 任务负责人
     */
    @TableField("sys_job_author")
    /** SYS作业author */
    private String sysJobAuthor;

    /**
     * 告警邮箱 (多个用逗号分隔)
     */
    @TableField("sys_job_alarm_email")
    /** SYS作业alarmemail */
    private String sysJobAlarmEmail;

    /**
     * 命名空间 (隔离多项目)
     */
    @TableField("sys_job_group")
    /** SYS作业分组 */
    private String sysJobGroup;

    /**
     * 调度类型 (CRON / FIXED / FIXED_MS / DELAY / AT / NONE)
     */
    @TableField("sys_job_schedule_type")
    /** SYS作业schedule类型 */
    private String sysJobScheduleType;

    /**
     * 调度配置 (cron 表达式或毫秒间隔)
     */
    @TableField("sys_job_schedule_time")
    /** SYS作业schedule时间 */
    private String sysJobScheduleTime;

    /**
     * 启用状态 0=停止 1=运行
     */
    @TableField("sys_job_trigger_status")
    /** SYS作业trigger状态 */
    private Integer sysJobTriggerStatus;

    /**
     * 任务描述
     */
    @TableField("sys_job_desc")
    /** SYS作业desc */
    private String sysJobDesc;

    /**
     * Glue 最后更新时间 (留扩展)
     */
    @TableField("sys_job_glue_updatetime")
    /** SYS作业glueupdatetime */
    private java.util.Date sysJobGlueUpdatetime;

    /**
     * Glue 源码 (留扩展)
     */
    @TableField("sys_job_glue_source")
    /** SYS作业glue来源 */
    private String sysJobGlueSource;

    /**
     * Glue 类型 (BEAN / GLUE_xxx)
     */
    @TableField("sys_job_glue_type")
    /** SYS作业glue类型 */
    private String sysJobGlueType;

    /**
     * 分发模式 (LOCAL / REMOTE)
     */
    @TableField("sys_job_dispatch_mode")
    /** SYS作业dispatch模式 */
    private String sysJobDispatchMode;

    /**
     * 远程执行器地址覆盖
     */
    @TableField("sys_job_remote_executor_address")
    /** SYS作业远程执行器地址 */
    private String sysJobRemoteExecutorAddress;

    /**
     * 日志存储模式 (DATABASE / FILE)
     */
    @TableField("sys_job_storage_mode")
    /** SYS作业存储模式 */
    private String sysJobStorageMode;

    /**
     * 失败重试次数
     */
    @TableField("sys_job_fail_retry")
    /** SYS作业fail重试 */
    private Integer sysJobFailRetry;

    /**
     * 重试间隔 (秒)
     */
    @TableField("sys_job_retry_interval")
    /** SYS作业重试间隔 */
    private Integer sysJobRetryInterval;

    /**
     * 执行超时 (秒)
     */
    @TableField("sys_job_execute_timeout")
    /** SYS作业execute超时 */
    private Integer sysJobExecuteTimeout;

    /**
     * 执行器 Handler 名称
     */
    @TableField("sys_job_execute_bean")
    /** SYS作业executebean */
    private String sysJobExecuteBean;

    /**
     * 执行参数
     */
    @TableField("sys_job_execute_param")
    /** SYS作业execute参数 */
    private String sysJobExecuteParam;

    /**
     * 异常回调 Handler
     */
    @TableField("sys_job_exception_callback_bean")
    /** SYS作业异常callbackbean */
    private String sysJobExceptionCallbackBean;

    /**
     * 重试前回调 Handler
     */
    @TableField("sys_job_retry_callback_bean")
    /** SYS作业重试callbackbean */
    private String sysJobRetryCallbackBean;

    /**
     * 错失触发策略
     */
    @TableField("sys_job_execute_misfire_strategy")
    /** SYS作业executemisfire策略 */
    private String sysJobExecuteMisfireStrategy;

    /**
     * 上次触发时间 (毫秒)
     */
    @TableField("sys_job_trigger_last_time")
    /** SYS作业trigger最后时间 */
    private Long sysJobTriggerLastTime;

    /**
     * 下次触发时间 (毫秒)
     */
    @TableField("sys_job_trigger_next_time")
    /** SYS作业trigger下一个时间 */
    private Long sysJobTriggerNextTime;

    /**
     * 逻辑删除标记 0=未删 1=已删
     */
    @TableField("sys_job_is_deleted")
    /** SYS作业IS删除标记 */
    private Integer sysJobIsDeleted;

    /**
     * 乐观锁版本
     */
    @TableField("sys_job_version")
    /** SYS作业版本 */
    private Long sysJobVersion;
}