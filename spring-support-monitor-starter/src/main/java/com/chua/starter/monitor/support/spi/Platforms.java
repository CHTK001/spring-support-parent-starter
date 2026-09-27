package com.chua.starter.monitor.support.spi;

import com.chua.starter.server.support.util.HostPlatforms;

/**
 * 操作系统平台判定。
 *
 * <p>取值域与主机台账列 {@code ms_server_host.ms_server_host_os_type} 一致，统一由
 * {@link HostPlatforms} 维护，本类只作为 monitor 侧的调用入口。接入方式（AGENT / LOCAL /
 * SCHEDULED）由 {@code ms_server_host_collect_mode} 表达，不是平台，凡拼装平台专属命令
 * （systemctl、taskkill、cd /d）的地方都必须先解析出真实平台。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class Platforms {

    /**
     * Windows 平台标识。
     */
    public static final String WINDOWS = HostPlatforms.WINDOWS;

    /**
     * Linux 平台标识。
     */
    public static final String LINUX = HostPlatforms.LINUX;

    /**
     * macOS 平台标识。
     */
    public static final String MACOS = HostPlatforms.MACOS;

    /**
     * 工具类，禁止实例化。
     */
    private Platforms() {
    }

    /**
     * 把任意平台描述串归一为 WINDOWS / LINUX / MACOS。
     *
     * @param raw 平台描述，如 {@code WINDOWS}、{@code Windows 11}、{@code darwin}、{@code macOS}
     * @return 归一后的平台标识；无法识别（含接入方式关键字）返回空串
     */
    public static String normalize(String raw) {
        return HostPlatforms.normalize(raw);
    }

    /**
     * 判断平台描述是否指向 Windows。
     *
     * @param raw 平台描述
     * @return 是 Windows 返回 true
     */
    public static boolean isWindows(String raw) {
        return WINDOWS.equals(normalize(raw));
    }
}
