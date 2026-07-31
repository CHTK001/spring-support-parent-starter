package com.chua.starter.common.support.serviceinfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANSI 颜色样式工具 (统一封装, 业务模块不应直接使用 {@code "\u001B[xxm"} 字面量)。
 *
 * <p>所有 stdout/stderr 输出经由此类, 避免在业务代码里到处散落 ANSI 字面量。
 * 也方便统一关闭颜色 (例如 no-TTY / 日志文件输出)。</p>
 *
 * <p>ANSI 渲染唯一实现位置: 本类 + 唯一调用点 {@link ServiceInfoAutoConfiguration},
 * 业务 starter 只通过 {@link ServiceInfoRegistry} 注册数据。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class AnsiStyle {

    private static final Logger log = LoggerFactory.getLogger(AnsiStyle.class);

    /** 是否启用颜色: 由环境变量 {@code NO_COLOR} 或 {@code TERM=dumb} 控制 */
    public static final boolean ENABLED = computeEnabled();

    public static final String RESET = wrap("\u001B[0m");
    public static final String BOLD = wrap("\u001B[1m");
    public static final String DIM = wrap("\u001B[2m");
    public static final String RED = wrap("\u001B[31m");
    public static final String GREEN = wrap("\u001B[32m");
    public static final String YELLOW = wrap("\u001B[33m");
    public static final String BLUE = wrap("\u001B[34m");
    public static final String CYAN = wrap("\u001B[36m");
    public static final String GRAY = wrap("\u001B[90m");
    public static final String BG_RED = wrap("\u001B[41m");

    private AnsiStyle() {}

    private static boolean computeEnabled() {
        try {
            if (System.getenv("NO_COLOR") != null) return false;
            String term = System.getenv("TERM");
            return term == null || !"dumb".equalsIgnoreCase(term);
        } catch (Throwable t) {
            log.debug("computeEnabled failed, fallback to false", t);
            return false;
        }
    }

    private static String wrap(String code) {
        return ENABLED ? code : "";
    }

    public static String color(String code, String text) {
        return ENABLED ? code + text + RESET : text;
    }

    public static String red(String s) { return color(RED, s); }
    public static String green(String s) { return color(GREEN, s); }
    public static String yellow(String s) { return color(YELLOW, s); }
    public static String cyan(String s) { return color(CYAN, s); }
    public static String blue(String s) { return color(BLUE, s); }
    public static String gray(String s) { return color(GRAY, s); }
    public static String bold(String s) { return color(BOLD, s); }

    /**
     * 三态开关的颜色化输出:
     * <ul>
     *     <li>{@code true} -> 绿色对勾</li>
     *     <li>{@code false} -> 红色叉</li>
     *     <li>{@code null} -> 灰色问号</li>
     * </ul>
     */
    public static String triFlag(Boolean value) {
        if (value == null) {
            return color(GRAY, "? unset");
        } else if (value) {
            return color(GREEN, "+ true ");
        } else {
            return color(RED, "x false");
        }
    }

    /**
     * 水平分隔线。
     */
    public static String separator(int width, char ch) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < width; i++) sb.append(ch);
        return ENABLED ? CYAN + sb.toString() + RESET : sb.toString();
    }
}