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
 * 业务 启动 只通过 {@link ServiceInfoRegistry} 注册数据。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class AnsiStyle {

    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(AnsiStyle.class);

    /**
     * 是否启用颜色: 由 {@link System#getenv(String) 环境变量} 动态读取。
     * <ul>
     *     <li>{@code NO_COLOR} 存在 → 关闭 (遵循 no-color.org 标准)</li>
     *     <li>{@code TERM=dumb} → 关闭</li>
     *     <li>其它情况 → 启用</li>
     * </ul>
     *
     * <p>使用 {@link ThreadLocal} 缓存, 避免每次调用都访问环境变量;
     * 测试场景可调用 {@link #reset()} 清缓存。</p>
     */
    private static final ThreadLocal<Boolean> ENABLED_CACHE = new ThreadLocal<>();

    /**
     * Reset
    */
    public static final String RESET = "\u001B[0m";
    /**
     * Bold
    */
    public static final String BOLD = "\u001B[1m";
    /**
     * DIM
    */
    public static final String DIM = "\u001B[2m";
    /**
     * 红色
    */
    public static final String RED = "\u001B[31m";
    /**
     * 绿色
    */
    public static final String GREEN = "\u001B[32m";
    /**
     * Yellow
    */
    public static final String YELLOW = "\u001B[33m";
    /**
     * 蓝色
    */
    public static final String BLUE = "\u001B[34m";
    /**
     * Cyan
    */
    public static final String CYAN = "\u001B[36m";
    /**
     * Gray
    */
    public static final String GRAY = "\u001B[90m";
    /**
     * Bg_R
    */
    public static final String BG_RED = "\u001B[41m";

    /**
     * 创建 ansistyle 实例
    */
    private AnsiStyle() {}

    /**
     * 当前是否启用 ANSI 颜色。
     * @return 是否已启用的结果
     */
    public static boolean isEnabled() {
        Boolean cached = ENABLED_CACHE.get();
        if (cached != null) {
            return cached;
        }
        boolean enabled = computeEnabled();
        ENABLED_CACHE.set(enabled);
        return enabled;
    }

    /**
     * compute已启用
     *
     * @return compute已启用的结果
     */
    private static boolean computeEnabled() {
        try {
            if (System.getenv("NO_COLOR") != null) {
                return false;
            }
            String term = System.getenv("TERM");
            return term == null || !"dumb".equalsIgnoreCase(term);
        } catch (Throwable t) {
            log.debug("computeEnabled failed, fallback to false", t);
            return false;
        }
    }

    /**
     * 清空当前线程的 已启用 缓存。
     *
     * <p>主要用于单元测试, 在修改 {@code NO_COLOR} 等环境变量后调用。
     * 生产代码不应调用此方法。</p>
     */
    public static void reset() {
        ENABLED_CACHE.remove();
    }

    /**
     * Wrap
     *
     * @param code 编码
     * @return wrap的结果
     */
    private static String wrap(String code) {
        return isEnabled() ? code : "";
    }

    /**
     * Color
     *
     * @param code 编码
     * @param text 文本
     * @return color的结果
     */
    public static String color(String code, String text) {
        return isEnabled() ? code + text + RESET : text;
    }

    /**
     * R
     *
     * @param s s
     * @return R的结果
     */
    public static String red(String s) { return color(RED, s); }
    /**
     * Green
     *
     * @param s s
     * @return green的结果
     */
    public static String green(String s) { return color(GREEN, s); }
    /**
     * Yellow
     *
     * @param s s
     * @return yellow的结果
     */
    public static String yellow(String s) { return color(YELLOW, s); }
    /**
     * Cyan
     *
     * @param s s
     * @return cyan的结果
     */
    public static String cyan(String s) { return color(CYAN, s); }
    /**
     * Blue
     *
     * @param s s
     * @return blue的结果
     */
    public static String blue(String s) { return color(BLUE, s); }
    /**
     * Gray
     *
     * @param s s
     * @return gray的结果
     */
    public static String gray(String s) { return color(GRAY, s); }
    /**
     * Bold
     *
     * @param s s
     * @return bold的结果
     */
    public static String bold(String s) { return color(BOLD, s); }

    /**
     * 三态开关的颜色化输出:
     * <ul>
     *     <li>{@code true} -> 绿色对勾</li>
     *     <li>{@code false} -> 红色叉</li>
     *     <li>{@code null} -> 灰色问号</li>
     * </ul>
     * @param value 值
     * @return triFlag的结果
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
     * 中文开关标签的颜色化输出:
     * <ul>
     *     <li>{@code true}  -> 绿色 {@code [开启]}</li>
     *     <li>{@code false} -> 红色 {@code [关闭]}</li>
     *     <li>{@code null}  -> 灰色 {@code [未配置]}</li>
     * </ul>
     *
     * <p>用于 {@link ServiceInfoAutoConfiguration} 渲染类似
     * {@code [API控制] [@ApiVersion] [版本控制] [开启]} 的横幅行。</p>
     * @param value 值
     * @return cn标签的结果
     */
    public static String cnLabel(Boolean value) {
        if (value == null) {
            return color(GRAY, "[未配置]");
        } else if (value) {
            return color(GREEN, "[开启]");
        } else {
            return color(RED, "[关闭]");
        }
    }

    /**
     * 包裹一段文本, 用指定 ANSI 代码染色。
     * @param ansiCode ansi编码
     * @param text 文本
     * @return wrap的结果
     */
    public static String wrap(String ansiCode, String text) {
        return color(ansiCode, text);
    }

    /**
     * 水平分隔线。
     * @param width width
     * @param ch ch
     * @return separator的结果
     */
    public static String separator(int width, char ch) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < width; i++) {
            sb.append(ch);
        }
        return isEnabled() ? CYAN + sb.toString() + RESET : sb.toString();
    }
}
