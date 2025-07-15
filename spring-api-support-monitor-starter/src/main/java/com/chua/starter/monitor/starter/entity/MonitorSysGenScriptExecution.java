package com.chua.starter.monitor.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 脚本执行历史实体
 * @author CH
 * @since 2024/12/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("monitor_sys_gen_script_execution")
public class MonitorSysGenScriptExecution extends SysBase {

    /**
     * 执行记录ID
     */
    @TableId(value = "monitor_sys_gen_script_execution_id", type = IdType.AUTO)
    private Long executionId;

    /**
     * 脚本ID
     */
    @TableField("monitor_sys_gen_script_id")
    private Integer scriptId;

    /**
     * 执行参数
     */
    @TableField("monitor_sys_gen_script_execution_parameters")
    private String executionParameters;

    /**
     * 执行状态
     */
    @TableField("monitor_sys_gen_script_execution_status")
    private ExecutionStatus executionStatus;

    /**
     * 开始执行时间
     */
    @TableField("monitor_sys_gen_script_execution_start_time")
    private LocalDateTime executionStartTime;

    /**
     * 结束执行时间
     */
    @TableField("monitor_sys_gen_script_execution_end_time")
    private LocalDateTime executionEndTime;

    /**
     * 执行耗时(毫秒)
     */
    @TableField("monitor_sys_gen_script_execution_duration")
    private Long executionDuration;

    /**
     * 退出码
     */
    @TableField("monitor_sys_gen_script_execution_exit_code")
    private Integer executionExitCode;

    /**
     * 标准输出
     */
    @TableField("monitor_sys_gen_script_execution_stdout")
    private String executionStdout;

    /**
     * 错误输出
     */
    @TableField("monitor_sys_gen_script_execution_stderr")
    private String executionStderr;

    /**
     * 错误信息
     */
    @TableField("monitor_sys_gen_script_execution_error_message")
    private String executionErrorMessage;

    /**
     * 进程ID
     */
    @TableField("monitor_sys_gen_script_execution_process_id")
    private Long executionProcessId;

    /**
     * 执行服务器ID
     */
    @TableField("monitor_sys_gen_script_execution_server_id")
    private Integer executionServerId;

    /**
     * 触发类型
     */
    @TableField("monitor_sys_gen_script_execution_trigger_type")
    private TriggerType executionTriggerType;

    /**
     * 执行状态枚举
     */
    public enum ExecutionStatus {
        RUNNING("RUNNING", "运行中"),
        SUCCESS("SUCCESS", "执行成功"),
        FAILED("FAILED", "执行失败"),
        TIMEOUT("TIMEOUT", "执行超时"),
        CANCELLED("CANCELLED", "已取消");

        private final String code;
        private final String desc;

        ExecutionStatus(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public static ExecutionStatus fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (ExecutionStatus status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
            return null;
        }
    }

    /**
     * 触发类型枚举
     */
    public enum TriggerType {
        MANUAL("MANUAL", "手动执行"),
        SCHEDULED("SCHEDULED", "定时执行"),
        API("API", "接口调用");

        private final String code;
        private final String desc;

        TriggerType(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public static TriggerType fromCode(String code) {
            if (code == null) {
                return TriggerType.MANUAL;
            }
            for (TriggerType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return TriggerType.MANUAL;
        }
    }

    /**
     * 检查执行是否成功
     */
    public boolean isSuccess() {
        return ExecutionStatus.SUCCESS.equals(executionStatus);
    }

    /**
     * 检查执行是否失败
     */
    public boolean isFailure() {
        return ExecutionStatus.FAILED.equals(executionStatus) || 
               ExecutionStatus.TIMEOUT.equals(executionStatus);
    }

    /**
     * 检查执行是否正在运行
     */
    public boolean isRunning() {
        return ExecutionStatus.RUNNING.equals(executionStatus);
    }

    /**
     * 获取格式化的执行耗时
     */
    public String getFormattedDuration() {
        if (executionDuration == null) {
            return "未知";
        }
        
        if (executionDuration < 1000) {
            return executionDuration + "ms";
        } else if (executionDuration < 60000) {
            return String.format("%.2fs", executionDuration / 1000.0);
        } else {
            long minutes = executionDuration / 60000;
            long seconds = (executionDuration % 60000) / 1000;
            return String.format("%dm%ds", minutes, seconds);
        }
    }

    /**
     * 计算执行耗时
     */
    public void calculateDuration() {
        if (executionStartTime != null && executionEndTime != null) {
            this.executionDuration = java.time.Duration.between(executionStartTime, executionEndTime).toMillis();
        }
    }

    /**
     * 设置执行完成
     */
    public void setExecutionCompleted(ExecutionStatus status, Integer exitCode, String stdout, String stderr) {
        this.executionStatus = status;
        this.executionEndTime = LocalDateTime.now();
        this.executionExitCode = exitCode;
        this.executionStdout = stdout;
        this.executionStderr = stderr;
        calculateDuration();
    }

    /**
     * 设置执行失败
     */
    public void setExecutionFailed(String errorMessage) {
        this.executionStatus = ExecutionStatus.FAILED;
        this.executionEndTime = LocalDateTime.now();
        this.executionErrorMessage = errorMessage;
        calculateDuration();
    }

    /**
     * 获取执行结果摘要
     */
    public String getExecutionSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("状态: ").append(executionStatus != null ? executionStatus.getDesc() : "未知");
        summary.append(", 耗时: ").append(getFormattedDuration());
        if (executionExitCode != null) {
            summary.append(", 退出码: ").append(executionExitCode);
        }
        return summary.toString();
    }
}
