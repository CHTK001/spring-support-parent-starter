package com.chua.report.client.starter.report;

import lombok.Getter;

/**
* 设备指标类型枚举
* <p>
* 定义可上报的指标类型，每个指标可独立配置上报间隔
* </p>
*
* @版本 1.0.0
* @since 2025/12/08
* @author CH
 */
@Getter
public enum MetricType {

    /**
    * CPU 使用率
    * @param "cpu" "cpu"
    * @param "CPU使用率" "CPU使用率"
    * @param 30 30
    * @return CPU的结果
    */
    CPU("cpu", "CPU使用率", 30),

    /**
    * 内存使用率
    * @param "memory" "内存"
    * @param "内存使用率" "内存使用率"
    * @param 30 30
    * @return 内存的结果
    */
    MEMORY("memory", "内存使用率", 30),

    /**
    * 磁盘使用率
    * @param "disk" "disk"
    * @param "磁盘使用率" "磁盘使用率"
    * @param 60 60
    * @return DISK的结果
    */
    DISK("disk", "磁盘使用率", 60),

    /**
    * 网络流量
    * @param "network" "network"
    * @param "网络流量" "网络流量"
    * @param 30 30
    * @return NETWORK的结果
    */
    NETWORK("network", "网络流量", 30),

    /**
    * 系统负载
    * @param "load" "加载"
    * @param "系统负载" "系统负载"
    * @param 30 30
    * @return 加载的结果
    */
    LOAD("load", "系统负载", 30),

    /**
    * 进程信息
    * @param "process" "处理"
    * @param "进程信息" "进程信息"
    * @param 60 60
    * @return 处理的结果
    */
    PROCESS("process", "进程信息", 60),

    /**
    * 温度信息
    * @param "temperature" "temperature"
    * @param "温度信息" "温度信息"
    * @param 120 120
    * @return TEMPERATURE的结果
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

    /**
    * 指标类型。
    * @param code 编码
    * @param description description
    * @param defaultIntervalSeconds 默认间隔seconds
    */
    MetricType(String code, String description, int defaultIntervalSeconds) {
        this.code = code;
        this.description = description;
        this.defaultIntervalSeconds = defaultIntervalSeconds;
    }

    /**
    * 根据代码获取枚举
    *
    * @param code 指标代码
    * @return 枚举值，未找到返回 空
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

    /**
    * 获取指标代码
    *
    * @return 指标代码
    */
    public String getCode() {
        return code;
    }
}
