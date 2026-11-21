package com.chua.starter.common.support.watchlog.console;

import java.util.Locale;

/**
 * 控制台日志等级。
 *
 * <p>用于「阈值过滤」：配置里给出最低打印等级后，低于该等级的行不进入日志；
 * 被过滤行的下级内容一并过滤，避免出现「父级缺失、子级残留」的错位输出。</p>
 *
 * <p>严重程度由低到高：{@code DEBUG < INFO < WARN < ERROR}。</p>
 *
 * @author CH
 * @since 4.0.0
 * @see ConsoleConfig
 */
public enum ConsoleLevel {

    /**
     * 调试，仅用于排查。
     */
    DEBUG(0, "DEBUG"),

    /**
     * 普通信息，默认等级。
     */
    INFO(1, "INFO"),

    /**
     * 警告。
     */
    WARN(2, "WARN"),

    /**
     * 错误。
     */
    ERROR(3, "ERROR");

    /**
     * 严重程度，数值越大越严重。
     */
    private final int severity;

    /**
     * 展示名称。
     */
    private final String label;

    /**
     * 构造等级。
     *
     * @param severity 严重程度
     * @param label    展示名称
     */
    ConsoleLevel(int severity, String label) {
        this.severity = severity;
        this.label = label;
    }

    /**
     * 获取严重程度。
     *
     * @return 严重程度，数值越大越严重
     */
    public int severity() {
        return severity;
    }

    /**
     * 获取展示名称。
     *
     * @return 大写等级名称
     */
    public String label() {
        return label;
    }

    /**
     * 判断当前等级是否达到阈值。
     *
     * @param threshold 阈值等级
     * @return 达到阈值返回 true
     */
    public boolean reachable(ConsoleLevel threshold) {
        return null == threshold || severity >= threshold.severity;
    }

    /**
     * 按名称解析等级，忽略大小写。
     *
     * @param name 等级名称
     * @return 对应等级；无法识别时返回 {@link #INFO}
     */
    public static ConsoleLevel of(String name) {
        if (null == name || name.trim().isEmpty()) {
            return INFO;
        }
        String key = name.trim().toUpperCase(Locale.ROOT);
        for (ConsoleLevel level : values()) {
            if (level.name().equals(key)) {
                return level;
            }
        }
        return INFO;
    }
}
