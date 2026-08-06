package com.chua.starter.monitor.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 监控采集任务实体
 *
 * @author CH
 * @since 2026/8/4
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "监控采集任务")
@TableName("ms_monitor_task")
public class MsMonitorTask extends SysBase {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 关联监控资产 id
     */
    @TableField("ms_monitor_task_monitor_id")
    @Schema(description = "关联监控资产 id", example = "1")
    private Long monitorId;

    /**
     * 任务名称
     */
    @TableField("ms_monitor_task_name")
    @Schema(description = "任务名称", example = "Redis INFO 采集")
    private String monitorTaskName;

    /**
     * 服务类型
     */
    @TableField("ms_monitor_task_service_type")
    @Schema(description = "服务类型: SERVER / REDIS / MYSQL / PG / ORACLE / MSSQL / MQ",
            example = "REDIS")
    private String serviceType;

    /**
     * 采集方式
     */
    @TableField("ms_monitor_task_provider")
    @Schema(description = "采集方式: LOCAL / TERMINAL / HTTP / PROMETHEUS", example = "LOCAL")
    private String provider;

    /**
     * 采集参数 JSON
     */
    @TableField("ms_monitor_task_collect_config")
    @Schema(description = "采集参数 JSON, 含账号密码/连接信息/命令/PromQL 等",
            example = "{\"redis_host\":\"192.168.1.10\",\"redis_port\":6379,\"redis_password\":\"***\"}")
    private String collectConfig;

    /**
     * cron 表达式
     */
    @TableField("ms_monitor_task_cron")
    @Schema(description = "cron 表达式", example = "0/10 * * * * ?")
    private String monitorTaskCron;

    /**
     * 超时(毫秒)
     */
    @TableField("ms_monitor_task_timeout")
    @Schema(description = "超时(毫秒)", example = "5000")
    private Integer monitorTaskTimeout;

    /**
     * 是否启用
     */
    @TableField("ms_monitor_task_enabled")
    @Schema(description = "是否启用 1启用 0禁用", example = "1")
    private Integer monitorTaskEnabled;

    /**
     * 备注
     */
    @TableField("ms_monitor_task_remark")
    @Schema(description = "备注", example = "Redis INFO 关键指标 10 秒一采")
    private String monitorTaskRemark;

    /**
     * 最近一次触发时间
     */
    @TableField("ms_monitor_task_last_run_time")
    @Schema(description = "最近一次触发时间", example = "2026-08-04T10:00:00")
    private LocalDateTime lastRunTime;
}
