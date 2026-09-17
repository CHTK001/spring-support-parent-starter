package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
* 批量作业实体（cron 计划任务）
* <p>
* 对应表 {@code ms_exec_job}，按 cron 表达式对一组主机周期性执行命令。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "批量计划任务")
@TableName("ms_exec_job")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsExecJob extends SysBase {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
    * 主键
    */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    /**
    * 任务名称
    */
    @TableField("ms_exec_job_name")
    @Schema(description = "任务名称", example = "每日清理临时文件")
    private String msExecJobName; // ms执行作业名称

    /**
    * cron 表达式
    */
    @TableField("ms_exec_job_cron")
    @Schema(description = "cron表达式", example = "0 0 3 * * ?")
    private String msExecJobCron; // ms执行作业cron

    /**
    * 执行命令
    */
    @TableField("ms_exec_job_command")
    @Schema(description = "执行命令", example = "find /tmp -mtime +7 -delete")
    private String msExecJobCommand; // ms执行作业命令

    /**
    * 目标主机 标识 集合（JSON 数组字符串）
    */
    @TableField("ms_exec_job_host_ids")
    @Schema(description = "目标主机ID集合(JSON数组)", example = "[1,2]")
    private String msExecJobHostIds; // ms执行作业主机标识

    /**
    * 单机命令超时（秒）
    */
    @TableField("ms_exec_job_timeout")
    @Schema(description = "超时(秒)", example = "60")
    private Integer msExecJobTimeout; // ms执行作业超时

    /**
    * 启用状态
    */
    @TableField("ms_exec_job_enabled")
    @Schema(description = "启用状态 1启用 0停用", example = "1")
    private Integer msExecJobEnabled; // ms执行作业启用

    /**
    * 上次运行时间
    */
    @TableField("ms_exec_job_last_run_time")
    @Schema(description = "上次运行时间")
    private LocalDateTime msExecJobLastRunTime; // ms执行作业最后一个运行时间

    /**
    * 下次运行时间（调度预计算，便于前端展示与快速判断）
    */
    @TableField("ms_exec_job_next_run_time")
    @Schema(description = "下次运行时间")
    private LocalDateTime msExecJobNextRunTime; // ms执行作业下一个运行时间

    /**
    * 备注
    */
    @TableField("ms_exec_job_remark")
    @Schema(description = "备注")
    private String msExecJobRemark; // ms执行作业评论
}
