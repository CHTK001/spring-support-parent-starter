package com.chua.starter.monitor.support.enums;

import lombok.Getter;

/**
 * 服务器子类型枚举
 *
 * @since 2026/8/4
 * @author CH
 */
@Getter
public enum ServerType {

    /**
     * Linux
     */
    LINUX("LINUX", "Linux"),
    /**
     * 窗口
     */
    WINDOWS("WINDOWS", "Windows"),
    /**
     * macOS(本地采集跳过)
     */
    MACOS("MACOS", "macOS"),
    /**
     * JVM 本地(以 Java 进程视角采集)
     */
    JVM("JVM", "JVM");

    /**
     * 编码
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    /**
     * 构造方法
     * @param code 编码
     * @param desc 描述
     * @return 返回值
    */
    ServerType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 通过编码获取枚举
     *
     * @param code 编码
     * @return 枚举值, 找不到时返回 {@link #LINUX}
     */
    public static ServerType of(String code) {
        if (code == null) {
            return LINUX;
        }
        for (ServerType value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        return LINUX;
    }
}
