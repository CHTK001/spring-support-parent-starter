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
 * 批量执行日志实体
 * <p>
 * 对应表 {@code server_exec_log}，统一记录批量命令与文件分发的执行结果。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "批量执行日志")
@TableName("server_exec_log")
@JsonIgnoreProperties(ignoreUnknown = true)
public class MsExecLog extends SysBase {

    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键", example = "1")
    private Long id; // 标识

    /**
     * 日志类型
     */
    @TableField("server_exec_log_type")
    @Schema(description = "类型: COMMAND / FILE / JOB", example = "COMMAND")
    private String serverExecLogType; // 执行日志类型

    /**
     * 主机 标识
     */
    @TableField("server_exec_log_host_id")
    @Schema(description = "主机ID", example = "1")
    private Long serverExecLogHostId; // 执行日志主机标识

    /**
     * 主机名称（冗余）
     */
    @TableField("server_exec_log_host_name")
    @Schema(description = "主机名称", example = "应用服务器-01")
    private String serverExecLogHostName; // 执行日志主机名称

    /**
     * 关联计划任务 标识（非任务执行为空）
     */
    @TableField("server_exec_log_job_id")
    @Schema(description = "关联任务ID", example = "1")
    private Long serverExecLogJobId; // 执行日志作业标识

    /**
     * 命令内容或文件目标路径
     */
    @TableField("server_exec_log_command")
    @Schema(description = "命令或文件路径", example = "df -h")
    private String serverExecLogCommand; // 执行日志命令

    /**
     * 执行输出（超长截断）
     */
    @TableField("server_exec_log_output")
    @Schema(description = "执行输出")
    private String serverExecLogOutput; // 执行日志输出

    /**
     * 退出码
     */
    @TableField("server_exec_log_exit_code")
    @Schema(description = "退出码", example = "0")
    private Integer serverExecLogExitCode; // 执行日志exit编码

    /**
     * 执行状态
     */
    @TableField("server_exec_log_status")
    @Schema(description = "状态: SUCCESS / FAIL", example = "SUCCESS")
    private String serverExecLogStatus; // 执行日志状态

    /**
     * 错误信息
     */
    @TableField("server_exec_log_error")
    @Schema(description = "错误信息")
    private String serverExecLogError; // 执行日志错误

    /**
     * 开始时间
     */
    @TableField("server_exec_log_start_time")
    @Schema(description = "开始时间")
    private LocalDateTime serverExecLogStartTime; // 执行日志启动时间

    /**
     * 结束时间
     */
    @TableField("server_exec_log_end_time")
    @Schema(description = "结束时间")
    private LocalDateTime serverExecLogEndTime; // 执行日志结束时间

    /**
     * 耗时（毫秒）
     */
    @TableField("server_exec_log_cost_ms")
    @Schema(description = "耗时(ms)", example = "120")
    private Long serverExecLogCostMs; // 执行日志costms
}
