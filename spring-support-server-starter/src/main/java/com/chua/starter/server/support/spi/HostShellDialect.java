package com.chua.starter.server.support.spi;

/**
 * 主机命令 shell 方言
 * <p>
 * 与 {@link HostRemoteProvider#exec} 一一对应：{@link HostRemoteProvider#shellDialect()} 声明
 * 该实现期望收到哪种语法的命令文本，上层据此决定「生成什么脚本 / 审计什么高危命令」。
 * </p>
 * <p>
 * 典型用途是 AI 生成启停/检测脚本时的提示词与高危命令审计规则：生成出来的脚本必须能
 * 在对应通道里直接执行（POSIX 走 sh/bash，Windows 走 PowerShell）。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public enum HostShellDialect {

    /**
     * POSIX shell（sh/bash/ksh）：Linux、macOS、FreeBSD、AIX、Solaris
     */
    POSIX("POSIX", "Linux", "bash"),

    /** PowerShell：Windows */
    POWERSHELL("POWERSHELL", "Windows", "PowerShell");

    /** 编码 */
    private final String code;

    /** 系统展示名（用于提示词中的「目标 OS=」） */
    private final String osName;

    /** 脚本语言展示名（用于提示词中的「只输出可直接执行的 X 脚本」） */
    private final String scriptName;

    /**
     * 构造
     *
     * @param code       编码
     * @param osName     系统展示名
     * @param scriptName 脚本语言展示名
     */
    HostShellDialect(String code, String osName, String scriptName) {
        this.code = code;
        this.osName = osName;
        this.scriptName = scriptName;
    }

    /**
     * 编码
     *
     * @return 编码
     */
    public String code() {
        return code;
    }

    /**
     * 系统展示名
     *
     * @return 展示名
     */
    public String osName() {
        return osName;
    }

    /**
     * 脚本语言展示名
     *
     * @return 展示名
     */
    public String scriptName() {
        return scriptName;
    }
}
