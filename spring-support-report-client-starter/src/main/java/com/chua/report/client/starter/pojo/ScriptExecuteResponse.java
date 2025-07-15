package com.chua.report.client.starter.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 脚本执行响应
 * @author CH
 * @since 2024/12/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptExecuteResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 执行是否成功
     */
    private Boolean success;

    /**
     * 脚本类型
     */
    private String scriptType;

    /**
     * 执行状态
     */
    private String status;

    /**
     * 退出码
     */
    private Integer exitCode;

    /**
     * 标准输出
     */
    private String stdout;

    /**
     * 错误输出
     */
    private String stderr;

    /**
     * 执行消息
     */
    private String message;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 执行耗时（毫秒）
     */
    private Long executionTime;

    /**
     * 进程ID
     */
    private Long processId;

    /**
     * 脚本文件路径
     */
    private String scriptFilePath;

    /**
     * 工作目录
     */
    private String workingDirectory;

    /**
     * 执行用户
     */
    private String executeUser;

    /**
     * 错误详情
     */
    private String errorDetails;

    /**
     * 是否超时
     */
    private Boolean timeout;

    /**
     * 是否被中断
     */
    private Boolean interrupted;

    /**
     * 执行状态枚举
     */
    public enum ExecutionStatus {
        PENDING("PENDING", "等待执行"),
        RUNNING("RUNNING", "正在执行"),
        SUCCESS("SUCCESS", "执行成功"),
        FAILED("FAILED", "执行失败"),
        TIMEOUT("TIMEOUT", "执行超时"),
        INTERRUPTED("INTERRUPTED", "执行中断"),
        CANCELLED("CANCELLED", "执行取消");

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
            for (ExecutionStatus status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
            return null;
        }
    }

    /**
     * 获取执行状态枚举
     */
    public ExecutionStatus getExecutionStatus() {
        return ExecutionStatus.fromCode(status);
    }

    /**
     * 创建成功响应
     */
    public static ScriptExecuteResponse success(String scriptType, String stdout, int exitCode) {
        return ScriptExecuteResponse.builder()
                .success(true)
                .scriptType(scriptType)
                .status(ExecutionStatus.SUCCESS.getCode())
                .stdout(stdout)
                .exitCode(exitCode)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建失败响应
     */
    public static ScriptExecuteResponse failure(String scriptType, String message, String stderr, int exitCode) {
        return ScriptExecuteResponse.builder()
                .success(false)
                .scriptType(scriptType)
                .status(ExecutionStatus.FAILED.getCode())
                .message(message)
                .stderr(stderr)
                .exitCode(exitCode)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建超时响应
     */
    public static ScriptExecuteResponse timeout(String scriptType, String message) {
        return ScriptExecuteResponse.builder()
                .success(false)
                .scriptType(scriptType)
                .status(ExecutionStatus.TIMEOUT.getCode())
                .message(message)
                .timeout(true)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建中断响应
     */
    public static ScriptExecuteResponse interrupted(String scriptType, String message) {
        return ScriptExecuteResponse.builder()
                .success(false)
                .scriptType(scriptType)
                .status(ExecutionStatus.INTERRUPTED.getCode())
                .message(message)
                .interrupted(true)
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建异常响应
     */
    public static ScriptExecuteResponse exception(String scriptType, String message, Exception e) {
        return ScriptExecuteResponse.builder()
                .success(false)
                .scriptType(scriptType)
                .status(ExecutionStatus.FAILED.getCode())
                .message(message)
                .errorDetails(e.getMessage())
                .endTime(LocalDateTime.now())
                .build();
    }

    /**
     * 检查执行是否成功
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }

    /**
     * 检查执行是否失败
     */
    public boolean isFailure() {
        return !isSuccess();
    }

    /**
     * 检查是否超时
     */
    public boolean isTimeout() {
        return Boolean.TRUE.equals(timeout);
    }

    /**
     * 检查是否被中断
     */
    public boolean isInterrupted() {
        return Boolean.TRUE.equals(interrupted);
    }

    /**
     * 获取执行耗时（毫秒）
     */
    public Long getExecutionTime() {
        if (executionTime != null) {
            return executionTime;
        }
        
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        
        return null;
    }

    /**
     * 获取格式化的执行耗时
     */
    public String getFormattedExecutionTime() {
        Long time = getExecutionTime();
        if (time == null) {
            return "未知";
        }
        
        if (time < 1000) {
            return time + "ms";
        } else if (time < 60000) {
            return String.format("%.2fs", time / 1000.0);
        } else {
            long minutes = time / 60000;
            long seconds = (time % 60000) / 1000;
            return String.format("%dm%ds", minutes, seconds);
        }
    }

    /**
     * 获取完整输出（标准输出 + 错误输出）
     */
    public String getFullOutput() {
        StringBuilder output = new StringBuilder();
        
        if (stdout != null && !stdout.trim().isEmpty()) {
            output.append("=== 标准输出 ===\n");
            output.append(stdout);
            output.append("\n");
        }
        
        if (stderr != null && !stderr.trim().isEmpty()) {
            output.append("=== 错误输出 ===\n");
            output.append(stderr);
            output.append("\n");
        }
        
        return output.toString();
    }

    /**
     * 设置执行时间范围
     */
    public ScriptExecuteResponse withTimeRange(LocalDateTime start, LocalDateTime end) {
        this.startTime = start;
        this.endTime = end;
        if (start != null && end != null) {
            this.executionTime = java.time.Duration.between(start, end).toMillis();
        }
        return this;
    }

    /**
     * 设置进程信息
     */
    public ScriptExecuteResponse withProcessInfo(Long processId, String workingDirectory, String executeUser) {
        this.processId = processId;
        this.workingDirectory = workingDirectory;
        this.executeUser = executeUser;
        return this;
    }

    /**
     * 设置脚本文件路径
     */
    public ScriptExecuteResponse withScriptFilePath(String scriptFilePath) {
        this.scriptFilePath = scriptFilePath;
        return this;
    }

    /**
     * 添加错误详情
     */
    public ScriptExecuteResponse withErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
        return this;
    }

    /**
     * 获取简要状态描述
     */
    public String getStatusDescription() {
        ExecutionStatus execStatus = getExecutionStatus();
        if (execStatus != null) {
            return execStatus.getDesc();
        }
        return status != null ? status : "未知状态";
    }

    /**
     * 获取结果摘要
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("脚本类型: ").append(scriptType != null ? scriptType : "未知");
        summary.append(", 状态: ").append(getStatusDescription());
        summary.append(", 退出码: ").append(exitCode != null ? exitCode : "未知");
        summary.append(", 耗时: ").append(getFormattedExecutionTime());
        return summary.toString();
    }
}
