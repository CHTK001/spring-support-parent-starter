package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 批量执行日志实体
 * <p>
 * 对应表 {@code ms_exec_log}，统一记录批量命令与文件分发的执行结果。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "批量执行日志")
@TableName("ms_exec_log")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsExecLog implements Serializable {

    /** Serial版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id;

    /**
     * 日志类型
     */
    @TableField("ms_exec_log_type")
    @Schema(description = "类型: COMMAND / FILE / JOB", example = "COMMAND")
    private String msExecLogType;

    /**
     * 主机 ID
     */
    @TableField("ms_exec_log_host_id")
    @Schema(description = "主机ID", example = "1")
    private Long msExecLogHostId;

    /**
     * 主机名称（冗余）
     */
    @TableField("ms_exec_log_host_name")
    @Schema(description = "主机名称", example = "应用服务器-01")
    private String msExecLogHostName;

    /**
     * 关联计划任务 ID（非任务执行为空）
     */
    @TableField("ms_exec_log_job_id")
    @Schema(description = "关联任务ID", example = "1")
    private Long msExecLogJobId;

    /**
     * 命令内容或文件目标路径
     */
    @TableField("ms_exec_log_command")
    @Schema(description = "命令或文件路径", example = "df -h")
    private String msExecLogCommand;

    /**
     * 执行输出（超长截断）
     */
    @TableField("ms_exec_log_output")
    @Schema(description = "执行输出")
    private String msExecLogOutput;

    /**
     * 退出码
     */
    @TableField("ms_exec_log_exit_code")
    @Schema(description = "退出码", example = "0")
    private Integer msExecLogExitCode;

    /**
     * 执行状态
     */
    @TableField("ms_exec_log_status")
    @Schema(description = "状态: SUCCESS / FAIL", example = "SUCCESS")
    private String msExecLogStatus;

    /**
     * 错误信息
     */
    @TableField("ms_exec_log_error")
    @Schema(description = "错误信息")
    private String msExecLogError;

    /**
     * 开始时间
     */
    @TableField("ms_exec_log_start_time")
    @Schema(description = "开始时间")
    private LocalDateTime msExecLogStartTime;

    /**
     * 结束时间
     */
    @TableField("ms_exec_log_end_time")
    @Schema(description = "结束时间")
    private LocalDateTime msExecLogEndTime;

    /**
     * 耗时（毫秒）
     */
    @TableField("ms_exec_log_cost_ms")
    @Schema(description = "耗时(ms)", example = "120")
    private Long msExecLogCostMs;
}
