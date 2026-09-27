package com.chua.starter.server.support.util;

import java.util.Locale;

/**
 * 主机操作系统平台归一。
 *
 * <p>台账列 {@code ms_server_host.ms_server_host_os_type} 只允许承载真实平台，
 * 取值域与 {@code HostDetectProvider.os()} / {@code HostRemoteProvider.supportedOsTypes()}
 * 对齐：{@link #LINUX}、{@link #WINDOWS}、{@link #MACOS}、{@link #FREEBSD}、{@link #AIX}、{@link #SOLARIS}。
 * 接入方式（AGENT / LOCAL / SCHEDULED）由 {@code ms_server_host_collect_mode} 表达，不得写入本列。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class HostPlatforms {

    /**
     * Linux 平台标识
     */
    public static final String LINUX = "LINUX";

    /**
     * Windows 平台标识
     */
    public static final String WINDOWS = "WINDOWS";

    /**
     * macOS 平台标识
     */
    public static final String MACOS = "MACOS";

    /**
     * FreeBSD 平台标识
     */
    public static final String FREEBSD = "FREEBSD";

    /**
     * AIX 平台标识
     */
    public static final String AIX = "AIX";

    /**
     * Solaris 平台标识
     */
    public static final String SOLARIS = "SOLARIS";

    /**
     * 接入方式关键字：这些词出现在 os_type 里说明调用方把通道写成了平台。
     */
    private static final java.util.Set<String> CHANNEL_TOKENS =
            java.util.Set.of("NONE", "OPENSSH", "WINRM", "AGENT", "LOCAL", "SCHEDULED", "SSH", "HTTP", "PROMETHEUS", "TERMINAL");

    /**
     * 工具类，禁止实例化。
     */
    private HostPlatforms() {
    }

    /**
     * 把任意平台描述串归一为台账取值域。
     *
     * @param raw 平台描述，如 {@code Windows}、{@code Windows 11}、{@code darwin}、{@code macOS}、{@code linux}
     * @return 归一后的平台标识；无法识别（含接入方式关键字）返回空串
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        if (CHANNEL_TOKENS.contains(key)) {
            return "";
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith("win")) {
            return WINDOWS;
        }
        if (lower.startsWith("mac") || lower.contains("darwin") || lower.equals("OSX") || lower.equals("OS X")) {
            return MACOS;
        }
        if (lower.contains("freebsd") || lower.contains("openbsd") || lower.contains("netbsd") || lower.contains("dragonfly")) {
            return FREEBSD;
        }
        if (lower.contains("aix")) {
            return AIX;
        }
        if (lower.contains("sunos") || lower.contains("solaris")) {
            return SOLARIS;
        }
        if (lower.startsWith("linux") || lower.contains("unix") || lower.equals("alpine")
                || lower.equals("centos") || lower.equals("ubuntu") || lower.equals("debian")
                || lower.equals("rhel") || lower.equals("fedora") || lower.equals("suse")) {
            return LINUX;
        }
        return "";
    }

    /**
     * 判断取值是否落在平台取值域内。
     *
     * @param osType 待判断值
     * @return true 表示是合法平台标识
     */
    public static boolean isPlatform(String osType) {
        if (osType == null || osType.isBlank()) {
            return false;
        }
        String key = osType.trim().toUpperCase(Locale.ROOT);
        return LINUX.equals(key) || WINDOWS.equals(key) || MACOS.equals(key)
                || FREEBSD.equals(key) || AIX.equals(key) || SOLARIS.equals(key);
    }
}
