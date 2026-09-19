package com.chua.starter.monitor.support.spi;

import java.util.Locale;

/**
 * 操作系统平台判定。
 *
 * <p>主机的 {@code serverHostOsType} 同时承载了接入方式（AGENT / LOCAL）与平台
 * （WINDOWS / LINUX / MAC），凡是拼装平台专属命令（systemctl、taskkill、cd /d）的地方
 * 都必须先解析出真实平台，否则会出现给 Agent 接入的 Windows 主机下发 systemctl。</p>
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
    public static final String MAC = "MAC";

    /**
     * 工具类，禁止实例化。
     */
    private Platforms() {
    }

    /**
     * 把任意平台描述串归一为 WINDOWS / LINUX / MAC。
     *
     * @param raw 平台描述，如 {@code WINDOWS}、{@code Windows 11}、{@code darwin}、{@code macOS}
     * @return 归一后的平台标识；无法识别返回空串
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("win")) {
            return WINDOWS;
        }
        if (key.startsWith("mac") || key.contains("darwin") || key.equals("osx") || key.equals("os x")) {
            return MAC;
        }
        if (key.startsWith("linux") || key.contains("unix") || key.contains("sunos")
                || key.contains("bsd") || key.equals("alpine") || key.equals("centos") || key.equals("ubuntu")) {
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
