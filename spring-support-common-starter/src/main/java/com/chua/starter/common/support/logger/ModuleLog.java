package com.chua.starter.common.support.logger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模块日志工具类
 * <p>
 * 提供统一的模块日志格式，支持 ANSI 颜色输出：
 * <ul>
 *     <li>[模块名称] - 模块标识前缀</li>
 *     <li>host:port - 青色高亮</li>
 *     <li>开启/关闭 - 绿色/红色高亮</li>
 * </ul>
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-28
 */
public final class ModuleLog {

    // ANSI 颜色码
    public static final String RESET = "\u001B[0m";
    public static final String GREEN = "\u001B[32m";
    public static final String RED = "\u001B[31m";
    public static final String CYAN = "\u001B[36m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String WHITE = "\u001B[37m";
    public static final String BOLD = "\u001B[1m";

    private final Logger log;
    private final String moduleName;

    private ModuleLog(String moduleName, Class<?> clazz) {
        this.moduleName = moduleName;
        this.log = LoggerFactory.getLogger(clazz);
    }

    private ModuleLog(String moduleName, Logger logger) {
        this.moduleName = moduleName;
        this.log = logger;
    }

    /**
     * 创建模块日志实例
     *
     * @param moduleName 模块名称
     * @param clazz      日志类
     * @return ModuleLog 实例
     */
    public static ModuleLog of(String moduleName, Class<?> clazz) {
        return new ModuleLog(moduleName, clazz);
    }

    /**
     * 创建模块日志实例
     *
     * @param moduleName 模块名称
     * @param logger     日志实例
     * @return ModuleLog 实例
     */
    public static ModuleLog of(String moduleName, Logger logger) {
        return new ModuleLog(moduleName, logger);
    }

    // ==================== 格式化方法 ====================

    /**
     * 格式化地址（青色）
     *
     * @param host 主机
     * @param port 端口
     * @return 格式化后的地址
     */
    public static String address(String host, int port) {
        return CYAN + host + ":" + port + RESET;
    }

    /**
     * 格式化地址（青色）
     *
     * @param address 地址字符串
     * @return 格式化后的地址
     */
    public static String address(String address) {
        return CYAN + address + RESET;
    }

    /**
     * 格式化开启状态（绿色）
     *
     * @return 格式化后的状态
     */
    public static String enabled() {
        return GREEN + "开启" + RESET;
    }

    /**
     * 格式化关闭状态（红色）
     *
     * @return 格式化后的状态
     */
    public static String disabled() {
        return RED + "关闭" + RESET;
    }

    /**
     * 格式化状态
     *
     * @param enabled 是否开启
     * @return 格式化后的状态
     */
    public static String status(boolean enabled) {
        return enabled ? enabled() : disabled();
    }

    /**
     * 格式化成功（绿色）
     *
     * @return 格式化后的成功
     */
    public static String success() {
        return GREEN + "成功" + RESET;
    }

    /**
     * 格式化失败（红色）
     *
     * @return 格式化后的失败
     */
    public static String failed() {
        return RED + "失败" + RESET;
    }

    /**
     * 格式化警告文本（黄色）
     *
     * @param text 文本
     * @return 格式化后的文本
     */
    public static String warn(String text) {
        return YELLOW + text + RESET;
    }

    /**
     * 格式化高亮文本（青色）
     *
     * @param text 文本
     * @return 格式化后的文本
     */
    public static String highlight(String text) {
        return CYAN + text + RESET;
    }

    /**
     * 格式化高亮文本（青色）
     *
     * @param value 数值
     * @return 格式化后的文本
     */
    public static String highlight(Object value) {
        return CYAN + String.valueOf(value) + RESET;
    }

    // ==================== 日志方法 ====================

    /**
     * INFO 日志
     *
     * @param message 消息
     * @param args    参数
     */
    public void info(String message, Object... args) {
        log.info("[{}] {}", moduleName, format(message, args));
    }

    /**
     * DEBUG 日志
     *
     * @param message 消息
     * @param args    参数
     */
    public void debug(String message, Object... args) {
        log.debug("[{}] {}", moduleName, format(message, args));
    }

    /**
     * WARN 日志
     *
     * @param message 消息
     * @param args    参数
     */
    public void warn(String message, Object... args) {
        log.warn("[{}] {}", moduleName, format(message, args));
    }

    /**
     * ERROR 日志
     *
     * @param message 消息
     * @param args    参数
     */
    public void error(String message, Object... args) {
        log.error("[{}] {}", moduleName, format(message, args));
    }

    /**
     * ERROR 日志（带异常）
     *
     * @param message   消息
     * @param throwable 异常
     */
    public void error(String message, Throwable throwable) {
        log.error("[{}] {}", moduleName, message, throwable);
    }

    /**
     * 打印启动日志
     *
     * @param host 主机
     * @param port 端口
     */
    public void started(String host, int port) {
        log.info("[{}] 服务已启动 {}", moduleName, address(host, port));
    }

    /**
     * 打印启动日志（带额外信息）
     *
     * @param host    主机
     * @param port    端口
     * @param message 额外信息
     */
    public void started(String host, int port, String message) {
        log.info("[{}] 服务已启动 {} {}", moduleName, address(host, port), message);
    }

    /**
     * 打印停止日志
     */
    public void stopped() {
        log.info("[{}] 服务已停止", moduleName);
    }

    /**
     * 打印功能状态日志
     *
     * @param feature 功能名称
     * @param enabled 是否开启
     */
    public void feature(String feature, boolean enabled) {
        log.info("[{}] {} [{}]", moduleName, feature, status(enabled));
    }

    /**
     * 打印功能状态日志（带额外信息）
     *
     * @param feature 功能名称
     * @param enabled 是否开启
     * @param extra   额外信息
     */
    public void feature(String feature, boolean enabled, String extra) {
        log.info("[{}] {} [{}] {}", moduleName, feature, status(enabled), extra);
    }

    // ==================== 私有方法 ====================

    private String format(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        // 简单的 {} 替换
        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        int i = 0;
        while (i < message.length()) {
            if (i < message.length() - 1 && message.charAt(i) == '{' && message.charAt(i + 1) == '}') {
                if (argIndex < args.length) {
                    sb.append(args[argIndex++]);
                } else {
                    sb.append("{}");
                }
                i += 2;
            } else {
                sb.append(message.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }
}
