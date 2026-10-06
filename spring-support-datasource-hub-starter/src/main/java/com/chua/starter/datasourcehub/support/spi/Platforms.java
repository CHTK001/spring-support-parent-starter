package com.chua.starter.datasourcehub.support.spi;

import java.util.Locale;
import java.util.Set;

/**
 * 操作系统平台判定。
 *
 * <p>取值域与主机台账列 {@code ms_server_host.ms_server_host_os_type} 一致。
 * 本类自包含实现（常量 + 归一逻辑就地复制自 server-starter 的 {@code HostPlatforms}，
 * 刻意不依赖 server-starter），接入方式（AGENT / LOCAL /
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
    public static final String WINDOWS = "WINDOWS";

    /**
     * Linux 平台标识。
     */
    public static final String LINUX = "LINUX";

    /**
     * macOS 平台标识。
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
    private static final Set<String> CHANNEL_TOKENS =
            Set.of("NONE", "OPENSSH", "WINRM", "AGENT", "LOCAL", "SCHEDULED", "SSH", "HTTP", "PROMETHEUS", "TERMINAL");

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
     * 判断平台描述是否指向 Windows。
     *
     * @param raw 平台描述
     * @return 是 Windows 返回 true
     */
    public static boolean isWindows(String raw) {
        return WINDOWS.equals(normalize(raw));
    }
}
