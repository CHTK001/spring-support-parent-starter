package com.chua.starter.common.support.watchlog.console;

import com.chua.common.support.spi.ServiceProvider;

/**
 * 控制台日志整合对象工厂。
 *
 * <p>通过 SPI 按名称加载 {@link Console} 实现，默认名称 {@code default}。
 * 未注册任何实现时回落到 {@link DefaultConsole}，保证调用方始终拿到可用实例。</p>
 *
 * <pre>{@code
 * // 默认：纯文本、两个空格缩进
 * Console console = Consoles.create();
 *
 * // 指定配置：开启颜色与耗时
 * Console console = Consoles.create(new ConsoleConfig().ansi(true).elapsed(true));
 * }</pre>
 *
 * @author CH
 * @since 4.0.0
 * @see Console
 * @see ConsoleConfig
 * @see DefaultConsole
 */
public final class Consoles {

    /**
     * 默认实现名称。
     */
    public static final String DEFAULT = "default";

    /**
     * 工具类禁止实例化。
     */
    private Consoles() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 创建默认配置的控制台。
     *
     * @return 新的控制台实例
     */
    public static Console create() {
        return create(new ConsoleConfig());
    }

    /**
     * 按指定配置创建控制台。
     *
     * @param config 配置；为空时使用默认配置
     * @return 新的控制台实例
     */
    public static Console create(ConsoleConfig config) {
        return create(DEFAULT, config);
    }

    /**
     * 按名称创建默认配置的控制台。
     *
     * @param name SPI 名称
     * @return 新的控制台实例，未命中时回落到默认实现
     */
    public static Console create(String name) {
        return create(name, new ConsoleConfig());
    }

    /**
     * 按名称与配置创建控制台。
     *
     * <p>配置通过构造器注入；SPI 实现若只有无参构造器（不认识配置），
     * 则回落到 {@link DefaultConsole}，避免「配置静默失效」。</p>
     *
     * @param name   SPI 名称
     * @param config 配置；为空时使用默认配置
     * @return 新的控制台实例，未命中时回落到默认实现
     */
    public static Console create(String name, ConsoleConfig config) {
        ConsoleConfig actual = null == config ? new ConsoleConfig() : config;
        Console console = ServiceProvider.of(Console.class).getNewExtension(name, actual);
        return null == console ? new DefaultConsole(actual) : console;
    }
}
