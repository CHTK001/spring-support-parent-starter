package com.chua.starter.common.support.watchlog.console;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.spi.annotations.SpiDefault;
import com.chua.common.support.utils.StringUtils;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 控制台日志整合对象默认实现。
 *
 * <p>行为由 {@link ConsoleConfig} 决定：</p>
 * <ul>
 *   <li>每级缩进 {@link ConsoleConfig#childConnector()}；</li>
 *   <li>等级低于 {@link ConsoleConfig#level()} 的行不输出，其下级一并过滤；</li>
 *   <li>{@link ConsoleConfig#ansi()} 开启时按等级染色，关闭时输出纯文本；</li>
 *   <li>{@link ConsoleConfig#mdcHeader()} 开启时首行前打印 MDC 与开始时间；</li>
 *   <li>{@link ConsoleConfig#elapsed()} 开启时末尾追加耗时。</li>
 * </ul>
 *
 * <p>所有节点共享同一份输出，{@code toString()} 与 {@code print()} 始终输出整份日志。
 * 实现不保证线程安全：一个实例（及其所有下级）不应跨线程共享。</p>
 *
 * @author CH
 * @since 4.0.0
 * @see Console
 * @see ConsoleConfig
 * @see ConsoleLevel
 */
@SpiDefault
@Spi({"default", "console", "text"})
public class DefaultConsole implements Console {

    /**
     * 耗时展示格式。
     */
    private static final String ELAPSED_TEMPLATE = "（耗时 %d ms）";

    /**
     * 开始时间展示格式。
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * 共享状态：输出缓冲、配置、起始时间。
     */
    private final Shared shared;

    /**
     * 当前层级，根对象为 0。
     */
    private final int depth;

    /**
     * 当前层级对应的日志等级。
     */
    private final ConsoleLevel level;

    /**
     * 创建默认配置的根对象。
     */
    public DefaultConsole() {
        this(new ConsoleConfig());
    }

    /**
     * 按指定配置创建根对象。
     *
     * @param config 配置；为空时使用默认配置
     */
    public DefaultConsole(ConsoleConfig config) {
        this.shared = new Shared(null == config ? new ConsoleConfig() : config);
        this.depth = 0;
        this.level = ConsoleLevel.INFO;
    }

    /**
     * 创建指定层级的对象。
     *
     * @param shared 共享状态
     * @param depth  当前层级
     * @param level  当前等级
     */
    private DefaultConsole(Shared shared, int depth, ConsoleLevel level) {
        this.shared = shared;
        this.depth = depth;
        this.level = null == level ? ConsoleLevel.INFO : level;
    }

    @Override
    public Console section(String line) {
        return append(level, line);
    }

    @Override
    public Console section(String line, Object... args) {
        return append(level, format(line, args));
    }

    @Override
    public Console info(String line) {
        return append(ConsoleLevel.INFO, line);
    }

    @Override
    public Console info(String line, Object... args) {
        return append(ConsoleLevel.INFO, format(line, args));
    }

    @Override
    public Console warn(String line) {
        return append(ConsoleLevel.WARN, line);
    }

    @Override
    public Console warn(String line, Object... args) {
        return append(ConsoleLevel.WARN, format(line, args));
    }

    @Override
    public Console error(String line) {
        return append(ConsoleLevel.ERROR, line);
    }

    @Override
    public Console error(String line, Object... args) {
        return append(ConsoleLevel.ERROR, format(line, args));
    }

    @Override
    public Console debug(String line) {
        return append(ConsoleLevel.DEBUG, line);
    }

    @Override
    public Console debug(String line, Object... args) {
        return append(ConsoleLevel.DEBUG, format(line, args));
    }

    @Override
    public Console end() {
        if (depth <= 0) {
            return this;
        }
        return new DefaultConsole(shared, depth - 1, level);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(shared.buffer);
        ConsoleConfig config = shared.config;
        if (config.elapsed() && result.length() > 0) {
            result.append(String.format(ELAPSED_TEMPLATE, shared.elapsed())).append('\n');
        }
        return result.toString();
    }

    @Override
    public void print() {
        PrintStream out = openStream();
        out.print(toString());
        out.flush();
    }

    /**
     * 追加一行日志并返回下一级对象。
     *
     * @param lineLevel 该行等级
     * @param line      日志内容
     * @return 下一级对象
     */
    private Console append(ConsoleLevel lineLevel, String line) {
        ConsoleConfig config = shared.config;
        if (!lineLevel.reachable(config.level())) {
            // 未达阈值的整段（含下级）都不进日志；层级也不推进，避免保留下来的子行
            // 带着「没有父级」的缩进出现。
            return new DefaultConsole(shared, depth, lineLevel);
        }
        if (shared.buffer.length() == 0) {
            appendHeader(shared.buffer, config);
        }
        String indent = null == config.childConnector() ? "" : config.childConnector().repeat(depth);
        String text = null == line ? "" : line;
        if (config.ansi()) {
            shared.buffer.append(config.color(lineLevel)).append(indent).append(text)
                    .append(ConsoleConfig.ANSI_RESET);
        } else {
            shared.buffer.append(indent).append(text);
        }
        shared.buffer.append('\n');
        return new DefaultConsole(shared, depth + 1, lineLevel);
    }

    /**
     * 追加 MDC 头部（首行专用）。
     *
     * @param buffer 输出缓冲
     * @param config 配置
     */
    private void appendHeader(StringBuilder buffer, ConsoleConfig config) {
        if (!config.mdcHeader()) {
            return;
        }
        String mdc = shared.mdc();
        String time = shared.startTime.format(TIME_FORMATTER);
        if (null == mdc || mdc.isEmpty()) {
            buffer.append('[').append(time).append("] ");
        } else {
            buffer.append('[').append(mdc).append("][").append(time).append("] ");
        }
    }

    /**
     * 按配置编码打开标准输出。
     *
     * @return 标准输出流
     */
    private PrintStream openStream() {
        return new PrintStream(System.out, true, shared.config.resolveCharset());
    }

    /**
     * 格式化日志模板，占位符语义与 {@code StringUtils.format} 一致。
     *
     * @param line 模板
     * @param args 参数
     * @return 格式化结果；无参数时原样返回
     */
    private static String format(String line, Object... args) {
        if (null == line || null == args || args.length == 0) {
            return line;
        }
        return StringUtils.format(line, args);
    }

    /**
     * 所有节点共享的状态。
     */
    private static final class Shared {

        /**
         * 输出缓冲。
         */
        private final StringBuilder buffer = new StringBuilder();

        /**
         * 配置。
         */
        private final ConsoleConfig config;

        /**
         * 创建时刻，用于耗时统计。
         */
        private final long startNanos = System.nanoTime();

        /**
         * 创建时刻的本地时间，用于头部展示。
         */
        private final LocalTime startTime = LocalTime.now();

        /**
         * 构造共享状态。
         *
         * @param config 配置
         */
        private Shared(ConsoleConfig config) {
            this.config = config;
        }

        /**
         * 获取已耗时（毫秒）。
         *
         * @return 毫秒数
         */
        private long elapsed() {
            return (System.nanoTime() - startNanos) / 1_000_000L;
        }

        /**
         * 获取 MDC 追踪标识。
         *
         * @return 追踪标识；无 MDC 上下文时为空串
         */
        private String mdc() {
            try {
                return org.slf4j.MDC.get("traceId");
            } catch (Throwable ignore) {
                // 未引入 slf4j 或未绑定实现时不影响日志输出。
                return "";
            }
        }
    }
}
