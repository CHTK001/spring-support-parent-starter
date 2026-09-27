package com.chua.starter.monitor.support.enums;

import lombok.Getter;

/**
 * 命令执行方言枚举
 * <p>
 * 描述 {@link com.chua.starter.monitor.support.spi.SystemInfoProvider#exec} 下发的命令
 * 在目标主机上实际运行的 shell，控制层据此拼装工作目录与退出码探针。
 * </p>
 *
 * @since 2026/9/21
 * @author CH
 */
@Getter
public enum ShellDialect {

    /**
     * POSIX sh -c
     */
    SH("/bin/sh", "POSIX Shell"),
    /**
     * Windows cmd.exe /c
     */
    CMD("cmd.exe", "命令提示符"),
    /**
     * PowerShell（SSH / WinRM 通道下的 Windows 主机）
     */
    POWERSHELL("powershell", "PowerShell");

    /**
     * 解释器名称
     */
    private final String code;
    /**
     * 中文描述
     */
    private final String desc;

    /**
     * 构造方法
     *
     * @param code 解释器名称
     * @param desc 描述
     */
    ShellDialect(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 是否为 Windows 系方言。
     *
     * @return 是返回 true
     */
    public boolean windows() {
        return this != SH;
    }
}
