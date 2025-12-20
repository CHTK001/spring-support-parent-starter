package com.chua.starter.job.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 定时任务实体
 * <p>
 * 对应数据库表 monitor_job，存储任务的配置信息。
 * 包括任务的调度配置、执行器配置、Glue脚本配置等。
 * </p>
 *
 * <h3>主要字段分类</h3>
 * <ul>
 *     <li><b>基本信息</b> - jobId, jobName, jobAuthor, jobDesc</li>
 *     <li><b>调度配置</b> - jobScheduleType, jobScheduleTime, jobTriggerStatus</li>
 *     <li><b>执行配置</b> - jobExecuteBean, jobExecuteParam, jobExecuteTimeout, jobFailRetry</li>
 *     <li><b>Glue配置</b> - jobGlueType, jobGlueSource, jobGlueUpdatetime</li>
 *     <li><b>触发状态</b> - jobTriggerLastTime, jobTriggerNextTime</li>
 * </ul>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/08
 * @see MonitorJobLog
 */
@Data
@TableName(value = "monitor_job")
public class MonitorJob implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @TableId(value = "job_id", type = IdType.AUTO)
    private Integer jobId;

    /**
     * 名称
     */
    @TableField(value = "job_name")
    private String jobName;

    /**
     * 任务类型; cron, fixed
     */
    @TableField(value = "job_schedule_type")
    private String jobScheduleType;

    /**
     * 时间配置
     */
    @TableField(value = "job_schedule_time")
    private String jobScheduleTime;

    /**
     * 负责人
     */
    @TableField(value = "job_author")
    private String jobAuthor;

    /**
     * 报警邮件
     */
    @TableField(value = "job_alarm_email")
    private String jobAlarmEmail;

    /**
     * 状态;0: 未启用； 1: 正在运行
     */
    @TableField(value = "job_trigger_status")
    private Integer jobTriggerStatus;

    /**
     * 描述
     */
    @TableField(value = "job_desc")
    private String jobDesc;

    /**
     * Glue最后更新时间
     */
    @TableField(value = "job_glue_updatetime")
    private Date jobGlueUpdatetime;

    /**
     * 源码
     */
    @TableField(value = "job_glue_source")
    private String jobGlueSource;

    /**
     * Glue类型（例如：脚本、JAR等）
     */
    @TableField(value = "job_glue_type")
    private String jobGlueType;

    /**
     * 失败重试次数
     */
    @TableField(value = "job_fail_retry")
    private Integer jobFailRetry;

    /**
     * 超时时间
     */
    @TableField(value = "job_execute_timeout")
    private Integer jobExecuteTimeout;

    /**
     * bean名称
     */
    @TableField(value = "job_execute_bean")
    private String jobExecuteBean;

    /**
     * 执行参数
     */
    @TableField(value = "job_execute_param")
    private String jobExecuteParam;

    /**
     * 失效后策略
     */
    @TableField(value = "job_execute_misfire_strategy")
    private String jobExecuteMisfireStrategy;

    /**
     * 任务触发的最后一次时间
     */
    @TableField(value = "job_trigger_last_time")
    private Long jobTriggerLastTime;

    /**
     * 任务触发的下一次时间
     */
    @TableField(value = "job_trigger_next_time")
    private Long jobTriggerNextTime;
}
