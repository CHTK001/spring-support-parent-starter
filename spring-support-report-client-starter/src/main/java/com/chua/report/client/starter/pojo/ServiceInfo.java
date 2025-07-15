package com.chua.report.client.starter.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 服务信息
 * @author CH
 * @since 2024/12/19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 服务名称
     */
    private String name;

    /**
     * 服务显示名称
     */
    private String displayName;

    /**
     * 服务描述
     */
    private String description;

    /**
     * 服务状态
     */
    private String state;

    /**
     * 启动类型
     */
    private String startType;

    /**
     * 服务路径
     */
    private String path;

    /**
     * 进程ID
     */
    private Long processId;

    /**
     * 服务类型
     */
    private String serviceType;

    /**
     * 错误控制
     */
    private String errorControl;

    /**
     * 依赖服务
     */
    private String[] dependencies;

    /**
     * 服务账户
     */
    private String serviceAccount;

    /**
     * 是否可以停止
     */
    private Boolean canStop;

    /**
     * 是否可以暂停
     */
    private Boolean canPause;

    /**
     * 是否可以继续
     */
    private Boolean canContinue;

    /**
     * 服务状态枚举
     */
    public enum ServiceState {
        RUNNING("RUNNING", "运行中"),
        STOPPED("STOPPED", "已停止"),
        STARTING("STARTING", "启动中"),
        STOPPING("STOPPING", "停止中"),
        PAUSED("PAUSED", "已暂停"),
        PAUSING("PAUSING", "暂停中"),
        CONTINUING("CONTINUING", "继续中"),
        UNKNOWN("UNKNOWN", "未知");

        private final String code;
        private final String desc;

        ServiceState(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public static ServiceState fromCode(String code) {
            for (ServiceState state : values()) {
                if (state.code.equals(code)) {
                    return state;
                }
            }
            return UNKNOWN;
        }
    }

    /**
     * 启动类型枚举
     */
    public enum StartType {
        AUTOMATIC("AUTOMATIC", "自动"),
        MANUAL("MANUAL", "手动"),
        DISABLED("DISABLED", "禁用"),
        DELAYED("DELAYED", "延迟自动"),
        UNKNOWN("UNKNOWN", "未知");

        private final String code;
        private final String desc;

        StartType(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public static StartType fromCode(String code) {
            for (StartType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }

    /**
     * 获取服务状态枚举
     */
    public ServiceState getServiceState() {
        return ServiceState.fromCode(state);
    }

    /**
     * 获取启动类型枚举
     */
    public StartType getStartTypeEnum() {
        return StartType.fromCode(startType);
    }

    /**
     * 获取状态描述
     */
    public String getStateDescription() {
        ServiceState serviceState = getServiceState();
        return serviceState.getDesc();
    }

    /**
     * 获取启动类型描述
     */
    public String getStartTypeDescription() {
        StartType startTypeEnum = getStartTypeEnum();
        return startTypeEnum.getDesc();
    }

    /**
     * 检查服务是否正在运行
     */
    public boolean isRunning() {
        return ServiceState.RUNNING.equals(getServiceState());
    }

    /**
     * 检查服务是否已停止
     */
    public boolean isStopped() {
        return ServiceState.STOPPED.equals(getServiceState());
    }

    /**
     * 检查服务是否可以启动
     */
    public boolean canStart() {
        ServiceState currentState = getServiceState();
        return ServiceState.STOPPED.equals(currentState) && 
               !StartType.DISABLED.equals(getStartTypeEnum());
    }

    /**
     * 检查服务是否可以停止
     */
    public boolean canStopService() {
        return Boolean.TRUE.equals(canStop) && isRunning();
    }

    /**
     * 检查服务是否可以暂停
     */
    public boolean canPauseService() {
        return Boolean.TRUE.equals(canPause) && isRunning();
    }

    /**
     * 检查服务是否可以继续
     */
    public boolean canContinueService() {
        return Boolean.TRUE.equals(canContinue) && 
               ServiceState.PAUSED.equals(getServiceState());
    }

    /**
     * 检查服务是否为系统服务
     */
    public boolean isSystemService() {
        return name != null && (
            name.startsWith("System") || 
            name.startsWith("Windows") ||
            name.startsWith("Microsoft")
        );
    }

    /**
     * 获取依赖服务数量
     */
    public int getDependencyCount() {
        return dependencies != null ? dependencies.length : 0;
    }

    /**
     * 获取服务摘要信息
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("服务: ").append(displayName != null ? displayName : name);
        summary.append(", 状态: ").append(getStateDescription());
        summary.append(", 启动类型: ").append(getStartTypeDescription());
        if (processId != null) {
            summary.append(", PID: ").append(processId);
        }
        return summary.toString();
    }

    /**
     * 创建简单服务信息
     */
    public static ServiceInfo createSimple(String name, String displayName, String state, String startType) {
        return ServiceInfo.builder()
                .name(name)
                .displayName(displayName)
                .state(state)
                .startType(startType)
                .build();
    }

    /**
     * 创建详细服务信息
     */
    public static ServiceInfo createDetailed(String name, String displayName, String description,
                                           String state, String startType, String path, Long processId) {
        return ServiceInfo.builder()
                .name(name)
                .displayName(displayName)
                .description(description)
                .state(state)
                .startType(startType)
                .path(path)
                .processId(processId)
                .build();
    }
}
