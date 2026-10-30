package com.chua.starter.monitor.support.enums;

import lombok.Getter;

/**
 * 采集方式枚举(提供者)
 * <p>
 * 定义 {@link com.chua.starter.monitor.support.spi.MetricProvider} 的实现分类,
 * 对应任务表 {@code ms_monitor_task.provider} 字段。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
 */
@Getter
public enum ProviderType {

    /**
     * 本地采集(oshi) - 仅 服务端 可用, macOS 跳过
     */
    LOCAL("LOCAL", "本地采集"),
    /**
     * SSH/终端执行自定义命令
     */
    TERMINAL("TERMINAL", "终端"),
    /**
     * HTTP 接口拉取
     */
    HTTP("HTTP", "HTTP 接口"),
    /**
     * Prometheus 拉取
     */
    PROMETHEUS("PROMETHEUS", "Prometheus");

    /**
     * 类型编码
     */
    private final String code;
    /**
     * 中文描述
     */
    private final String desc;

    /**
     * 构造方法
     * @param code 编码
     * @param desc 描述
     * @return 返回值
    */
    ProviderType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 通过编码获取枚举
     *
     * @param code 编码
     * @return 枚举值, 找不到时返回 {@link #LOCAL}
     */
    public static ProviderType of(String code) {
        if (code == null) {
            return LOCAL;
        }
        for (ProviderType value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        return LOCAL;
    }
}
