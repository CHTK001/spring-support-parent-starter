package com.chua.report.client.starter.report;

import lombok.Getter;

/**
 * 设备指标类型枚举
 * <p>
 * 定义可上报的指标类型，每个指标可独立配置上报间隔
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025/12/08
 */
@Getter
public enum MetricType {

    /**
     * CPU 使用率
     */
    CPU("cpu", "CPU使用率", 30),

    /**
     * 内存使用率
     */
    MEMORY("memory", "内存使用率", 30),

    /**
     * 磁盘使用率
     */
    DISK("disk", "磁盘使用率", 60),

    /**
     * 网络流量
     */
    NETWORK("network", "网络流量", 30),

    /**
     * 系统负载
     */
    LOAD("load", "系统负载", 30),

    /**
     * 进程信息
     */
    PROCESS("process", "进程信息", 60),

    /**
     * 温度信息
     */
    TEMPERATURE("temperature", "温度信息", 120);

    /**
     * 指标代码
     */
    private final String code;

    /**
     * 指标描述
     */
    private final String description;

    /**
     * 默认上报间隔（秒）
     */
    private final int defaultIntervalSeconds;

    MetricType(String code, String description, int defaultIntervalSeconds) {
        this.code = code;
        this.description = description;
        this.defaultIntervalSeconds = defaultIntervalSeconds;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 指标代码
     * @return 枚举值，未找到返回 null
     */
    public static MetricType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MetricType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}
