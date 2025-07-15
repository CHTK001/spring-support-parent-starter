package com.chua.report.client.starter.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 进程信息
 * @author CH
 * @since 2024/12/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 进程ID
     */
    private Long processId;

    /**
     * 父进程ID
     */
    private Long parentProcessId;

    /**
     * 进程名称
     */
    private String name;

    /**
     * 进程路径
     */
    private String path;

    /**
     * 命令行
     */
    private String commandLine;

    /**
     * 进程状态
     */
    private String state;

    /**
     * 用户名
     */
    private String user;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 组ID
     */
    private String groupId;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 线程数
     */
    private Integer threadCount;

    /**
     * 句柄数
     */
    private Integer handleCount;

    /**
     * 虚拟内存大小
     */
    private Long virtualSize;

    /**
     * 常驻内存大小
     */
    private Long residentSetSize;

    /**
     * 内存使用率
     */
    private Double memoryPercent;

    /**
     * CPU使用率
     */
    private Double cpuPercent;

    /**
     * 启动时间
     */
    private LocalDateTime startTime;

    /**
     * 运行时间（毫秒）
     */
    private Long upTime;

    /**
     * 内核时间
     */
    private Long kernelTime;

    /**
     * 用户时间
     */
    private Long userTime;

    /**
     * 读取字节数
     */
    private Long bytesRead;

    /**
     * 写入字节数
     */
    private Long bytesWritten;

    /**
     * 打开文件数
     */
    private Integer openFiles;

    /**
     * 进程状态枚举
     */
    public enum ProcessState {
        RUNNING("RUNNING", "运行中"),
        SLEEPING("SLEEPING", "睡眠"),
        WAITING("WAITING", "等待"),
        ZOMBIE("ZOMBIE", "僵尸"),
        STOPPED("STOPPED", "停止"),
        TRACED("TRACED", "跟踪"),
        DEAD("DEAD", "死亡"),
        UNKNOWN("UNKNOWN", "未知");

        private final String code;
        private final String desc;

        ProcessState(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public static ProcessState fromCode(String code) {
            for (ProcessState state : values()) {
                if (state.code.equals(code)) {
                    return state;
                }
            }
            return UNKNOWN;
        }
    }

    /**
     * 获取进程状态枚举
     */
    public ProcessState getProcessState() {
        return ProcessState.fromCode(state);
    }

    /**
     * 获取格式化的内存大小
     */
    public String getFormattedMemorySize() {
        if (residentSetSize == null) {
            return "未知";
        }
        return formatBytes(residentSetSize);
    }

    /**
     * 获取格式化的虚拟内存大小
     */
    public String getFormattedVirtualSize() {
        if (virtualSize == null) {
            return "未知";
        }
        return formatBytes(virtualSize);
    }

    /**
     * 获取格式化的运行时间
     */
    public String getFormattedUpTime() {
        if (upTime == null) {
            return "未知";
        }
        
        long seconds = upTime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%d天%d小时", days, hours % 24);
        } else if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, seconds % 60);
        } else {
            return String.format("%d秒", seconds);
        }
    }

    /**
     * 获取格式化的CPU使用率
     */
    public String getFormattedCpuPercent() {
        if (cpuPercent == null) {
            return "0.0%";
        }
        return String.format("%.1f%%", cpuPercent);
    }

    /**
     * 获取格式化的内存使用率
     */
    public String getFormattedMemoryPercent() {
        if (memoryPercent == null) {
            return "0.0%";
        }
        return String.format("%.1f%%", memoryPercent);
    }

    /**
     * 获取进程状态描述
     */
    public String getStateDescription() {
        ProcessState processState = getProcessState();
        return processState.getDesc();
    }

    /**
     * 检查进程是否正在运行
     */
    public boolean isRunning() {
        return ProcessState.RUNNING.equals(getProcessState());
    }

    /**
     * 检查进程是否为系统进程
     */
    public boolean isSystemProcess() {
        return processId != null && processId <= 1000;
    }

    /**
     * 获取进程摘要信息
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("PID: ").append(processId);
        summary.append(", 名称: ").append(name != null ? name : "未知");
        summary.append(", 状态: ").append(getStateDescription());
        summary.append(", CPU: ").append(getFormattedCpuPercent());
        summary.append(", 内存: ").append(getFormattedMemorySize());
        summary.append(", 运行时间: ").append(getFormattedUpTime());
        return summary.toString();
    }

    /**
     * 格式化字节数
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 创建简单进程信息
     */
    public static ProcessInfo createSimple(long processId, String name, String state) {
        return ProcessInfo.builder()
                .processId(processId)
                .name(name)
                .state(state)
                .build();
    }

    /**
     * 创建详细进程信息
     */
    public static ProcessInfo createDetailed(long processId, String name, String path, String commandLine,
                                           String state, String user, double cpuPercent, double memoryPercent,
                                           long residentSetSize, LocalDateTime startTime) {
        return ProcessInfo.builder()
                .processId(processId)
                .name(name)
                .path(path)
                .commandLine(commandLine)
                .state(state)
                .user(user)
                .cpuPercent(cpuPercent)
                .memoryPercent(memoryPercent)
                .residentSetSize(residentSetSize)
                .startTime(startTime)
                .build();
    }
}
