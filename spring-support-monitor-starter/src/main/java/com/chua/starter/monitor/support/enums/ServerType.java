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
    * @param "LINUX" "Linux"
    * @param "Linux" "Linux"
    * @return Linux的结果
     */
    LINUX("LINUX", "Linux"),
    /**
    * 窗口
    * @param "WINDOWS" "窗口"
    * @param "Windows" "窗口"
    * @return 窗口的结果
     */
    WINDOWS("WINDOWS", "Windows"),
    /**
    * macOS(本地采集跳过)
    * @param "MACOS" "macOS"
    * @param "macOS" "macOS"
    * @return macOS的结果
     */
    MACOS("MACOS", "macOS"),
    /**
    * JVM 本地(以 Java 进程视角采集)
    * @param "JVM" "JVM"
    * @param "JVM" "JVM"
    * @return JVM的结果
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
    *
    * @param code 编码
    * @param desc 描述
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
