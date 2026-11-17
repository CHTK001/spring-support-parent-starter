package com.chua.starter.common.support.watchlog.console;

import com.chua.common.support.lang.cmd.OsFamily;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 控制台日志整合配置。
 *
 * <p>所有项都有默认值，默认值即「纯文本、零装饰」——输出与 {@link Console} 示例完全一致：</p>
 * <table border="1">
 *   <caption>默认值</caption>
 *   <tr><th>配置项</th><th>默认值</th><th>说明</th></tr>
 *   <tr><td>{@link #childConnector()}</td><td>两个空格</td><td>每级缩进</td></tr>
 *   <tr><td>{@link #mdcHeader()}</td><td>{@code false}</td><td>首行前打印 MDC 与开始时间</td></tr>
 *   <tr><td>{@link #ansi()}</td><td>{@code false}</td><td>按等级染色（Windows 老控制台不支持）</td></tr>
 *   <tr><td>{@link #elapsed()}</td><td>{@code false}</td><td>行尾追加耗时</td></tr>
 *   <tr><td>{@link #level()}</td><td>{@link ConsoleLevel#INFO}</td><td>低于阈值不输出</td></tr>
 *   <tr><td>{@link #resolveCharset()}</td><td>平台自动</td><td>Windows 用 GBK，其余 UTF-8</td></tr>
 * </table>
 *
 * <p>配置对象不可变，每个 setter 返回新实例，可链式调用：</p>
 *
 * <pre>{@code
 * ConsoleConfig config = new ConsoleConfig().ansi(true).elapsed(true).level("DEBUG");
 * Console console = Consoles.create(config);
 * }</pre>
 *
 * @author CH
 * @since 4.0.0
 * @see Console
 * @see ConsoleLevel
 * @see DefaultConsole
 */
public final class ConsoleConfig {

    /**
     * 默认子级连接符：两个空格。
     */
    public static final String DEFAULT_CHILD_CONNECTOR = "  ";

    /**
     * ESC 字符，ANSI 转义序列的起始字符。
     */
    public static final char ESC = (char) 27;

    /**
     * ANSI 转义前缀（{@code ESC} + {@code [}）。
     */
    public static final String ANSI_ESCAPE = ESC + "[";

    /**
     * 默认 INFO 颜色（ANSI 青色）。
     */
    public static final String DEFAULT_INFO_COLOR = ANSI_ESCAPE + "36m";

    /**
     * 默认 WARN 颜色（ANSI 黄色）。
     */
    public static final String DEFAULT_WARN_COLOR = ANSI_ESCAPE + "33m";

    /**
     * 默认 ERROR 颜色（ANSI 红色）。
     */
    public static final String DEFAULT_ERROR_COLOR = ANSI_ESCAPE + "31m";

    /**
     * 默认 DEBUG 颜色（ANSI 灰色）。
     */
    public static final String DEFAULT_DEBUG_COLOR = ANSI_ESCAPE + "90m";

    /**
     * ANSI 颜色重置。
     */
    public static final String ANSI_RESET = ANSI_ESCAPE + "0m";

    /**
     * 子级连接符。
     */
    private String childConnector;

    /**
     * 是否在最前面打印 MDC 标识与时间。
     */
    private boolean mdcHeader;

    /**
     * 是否开启 ANSI 颜色。
     */
    private boolean ansi;

    /**
     * INFO 颜色。
     */
    private String infoColor;

    /**
     * WARN 颜色。
     */
    private String warnColor;

    /**
     * ERROR 颜色。
     */
    private String errorColor;

    /**
     * DEBUG 颜色。
     */
    private String debugColor;

    /**
     * 是否打印耗时。
     */
    private boolean elapsed;

    /**
     * 等级阈值。
     */
    private ConsoleLevel level;

    /**
     * 输出编码；为空表示按平台自动选择。
     */
    private Charset charset;

    /**
     * 创建默认配置。
     */
    public ConsoleConfig() {
        this.childConnector = DEFAULT_CHILD_CONNECTOR;
        this.mdcHeader = false;
        this.ansi = false;
        this.infoColor = DEFAULT_INFO_COLOR;
        this.warnColor = DEFAULT_WARN_COLOR;
        this.errorColor = DEFAULT_ERROR_COLOR;
        this.debugColor = DEFAULT_DEBUG_COLOR;
        this.elapsed = false;
        this.level = ConsoleLevel.INFO;
        this.charset = null;
    }

    /**
     * 创建指定子级连接符的配置。
     *
     * @param childConnector 子级连接符；为空时回落默认值
     */
    public ConsoleConfig(String childConnector) {
        this();
        this.childConnector = null == childConnector ? DEFAULT_CHILD_CONNECTOR : childConnector;
    }

    /**
     * 设置子级连接符。
     *
     * @param childConnector 子级连接符；为空时回落默认值
     * @return 新配置
     */
    public ConsoleConfig childConnector(String childConnector) {
        ConsoleConfig config = copy();
        config.childConnector = null == childConnector ? DEFAULT_CHILD_CONNECTOR : childConnector;
        return config;
    }

    /**
     * 设置是否在最前面打印 MDC 标识与开始时间。
     *
     * @param mdcHeader 是否开启
     * @return 新配置
     */
    public ConsoleConfig mdcHeader(boolean mdcHeader) {
        ConsoleConfig config = copy();
        config.mdcHeader = mdcHeader;
        return config;
    }

    /**
     * 设置是否开启 ANSI 颜色。
     *
     * @param ansi 是否开启
     * @return 新配置
     */
    public ConsoleConfig ansi(boolean ansi) {
        ConsoleConfig config = copy();
        config.ansi = ansi;
        return config;
    }

    /**
     * 设置 INFO 颜色。
     *
     * @param infoColor ANSI 颜色序列
     * @return 新配置
     */
    public ConsoleConfig infoColor(String infoColor) {
        ConsoleConfig config = copy();
        config.infoColor = infoColor;
        return config;
    }

    /**
     * 设置 WARN 颜色。
     *
     * @param warnColor ANSI 颜色序列
     * @return 新配置
     */
    public ConsoleConfig warnColor(String warnColor) {
        ConsoleConfig config = copy();
        config.warnColor = warnColor;
        return config;
    }

    /**
     * 设置 ERROR 颜色。
     *
     * @param errorColor ANSI 颜色序列
     * @return 新配置
     */
    public ConsoleConfig errorColor(String errorColor) {
        ConsoleConfig config = copy();
        config.errorColor = errorColor;
        return config;
    }

    /**
     * 设置 DEBUG 颜色。
     *
     * @param debugColor ANSI 颜色序列
     * @return 新配置
     */
    public ConsoleConfig debugColor(String debugColor) {
        ConsoleConfig config = copy();
        config.debugColor = debugColor;
        return config;
    }

    /**
     * 设置是否打印耗时。
     *
     * @param elapsed 是否开启
     * @return 新配置
     */
    public ConsoleConfig elapsed(boolean elapsed) {
        ConsoleConfig config = copy();
        config.elapsed = elapsed;
        return config;
    }

    /**
     * 设置等级阈值。
     *
     * @param level 阈值等级；为空时回落 {@link ConsoleLevel#INFO}
     * @return 新配置
     */
    public ConsoleConfig level(ConsoleLevel level) {
        ConsoleConfig config = copy();
        config.level = null == level ? ConsoleLevel.INFO : level;
        return config;
    }

    /**
     * 按名称设置等级阈值，忽略大小写，无法识别时回落 {@link ConsoleLevel#INFO}。
     *
     * @param level 阈值等级名称
     * @return 新配置
     */
    public ConsoleConfig level(String level) {
        return level(ConsoleLevel.of(level));
    }

    /**
     * 设置输出编码。
     *
     * @param charset 输出编码；为空时按平台自动选择
     * @return 新配置
     */
    public ConsoleConfig charset(Charset charset) {
        ConsoleConfig config = copy();
        config.charset = charset;
        return config;
    }

    /**
     * 按名称设置输出编码，无法识别时回落平台自动选择。
     *
     * @param charsetName 编码名称，如 {@code "UTF-8"}、{@code "GBK"}
     * @return 新配置
     */
    public ConsoleConfig charset(String charsetName) {
        if (null == charsetName || charsetName.trim().isEmpty()) {
            return charset((Charset) null);
        }
        try {
            return charset(Charset.forName(charsetName.trim()));
        } catch (Exception e) {
            return charset((Charset) null);
        }
    }

    /**
     * 获取子级连接符。
     *
     * @return 子级连接符
     */
    public String childConnector() {
        return childConnector;
    }

    /**
     * 是否打印 MDC 标识与时间。
     *
     * @return 开启返回 true
     */
    public boolean mdcHeader() {
        return mdcHeader;
    }

    /**
     * 是否开启 ANSI 颜色。
     *
     * @return 开启返回 true
     */
    public boolean ansi() {
        return ansi;
    }

    /**
     * 获取 INFO 颜色。
     *
     * @return ANSI 颜色序列
     */
    public String infoColor() {
        return infoColor;
    }

    /**
     * 获取 WARN 颜色。
     *
     * @return ANSI 颜色序列
     */
    public String warnColor() {
        return warnColor;
    }

    /**
     * 获取 ERROR 颜色。
     *
     * @return ANSI 颜色序列
     */
    public String errorColor() {
        return errorColor;
    }

    /**
     * 获取 DEBUG 颜色。
     *
     * @return ANSI 颜色序列
     */
    public String debugColor() {
        return debugColor;
    }

    /**
     * 是否打印耗时。
     *
     * @return 开启返回 true
     */
    public boolean elapsed() {
        return elapsed;
    }

    /**
     * 获取等级阈值。
     *
     * @return 阈值等级
     */
    public ConsoleLevel level() {
        return level;
    }

    /**
     * 获取实际使用的输出编码。
     *
     * <p>未显式配置时按平台自动选择：Windows 用 GBK（与该平台控制台默认一致），
     * 其余平台用 UTF-8。</p>
     *
     * @return 输出编码，不会为 {@code null}
     */
    public Charset resolveCharset() {
        if (null != charset) {
            return charset;
        }
        return OsFamily.current().isWindows() ? Charset.forName("GBK") : StandardCharsets.UTF_8;
    }

    /**
     * 获取指定等级对应的颜色。
     *
     * @param level 等级
     * @return 该等级的颜色序列
     */
    public String color(ConsoleLevel level) {
        if (null == level) {
            return infoColor;
        }
        switch (level) {
            case WARN:
                return warnColor;
            case ERROR:
                return errorColor;
            case DEBUG:
                return debugColor;
            default:
                return infoColor;
        }
    }

    /**
     * 复制当前配置。
     *
     * @return 配置副本
     */
    private ConsoleConfig copy() {
        ConsoleConfig config = new ConsoleConfig();
        config.childConnector = childConnector;
        config.mdcHeader = mdcHeader;
        config.ansi = ansi;
        config.infoColor = infoColor;
        config.warnColor = warnColor;
        config.errorColor = errorColor;
        config.debugColor = debugColor;
        config.elapsed = elapsed;
        config.level = level;
        config.charset = charset;
        return config;
    }
}
