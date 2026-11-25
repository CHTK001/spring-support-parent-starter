package com.chua.starter.common.support.log;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志配置属性（{@code plugin.log.*}）。
 *
 * <p>控制参数日志过滤器、接口日志事件与慢请求告警阈值。</p>
 *
 * <p><b>完整 YAML 配置示例：</b></p>
 * <pre>{@code
 * plugin:
 *   log:
 *     enable: true                    # 是否启用参数日志过滤器（默认 true）
 *     open-interface-log: false       # 是否开启接口日志事件（默认 false）
 *     slow-request-threshold-ms: 3000 # 慢请求 WARN 阈值（毫秒，默认 3000）
 * }</pre>
 *
 * <p>注意：参数日志过滤器 Bean 的注册条件为 {@code plugin.log.enable=true}
 * （未显式配置时不注册）；MDC 链路追踪默认永久开启，无需配置。</p>
 *
 * @since 2024/01/01
 * @author CH
 */
@ConfigurationProperties(prefix = "plugin.log", ignoreInvalidFields = true)
public class LogProperties {

    /**
     * 日志开启。
     *
     * <p>默认 {@code true}；参数日志过滤器的注册条件为本项显式配置为 {@code true}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   log:
     *     enable: true
     * }</pre>
     */
    private boolean enable = true;

    /**
     * 开启接口日志。
     *
     * <p>默认 {@code false}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   log:
     *     open-interface-log: false
     * }</pre>
     */
    private boolean openInterfaceLog = false;

    /**
     * 慢请求阈值（毫秒），超过此值将打印 WARN 日志。
     *
     * <p>默认 {@code 3000}。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>{@code
     * plugin:
     *   log:
     *     slow-request-threshold-ms: 3000
     * }</pre>
     */
    // [P3C A20 豁免]
    private long slowRequestThresholdMs = 3000;

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 判断是否启用
     *
     * @return enable
     */
    public boolean isEnable() {
        return enable;
    }

    /**
     * 设置 enable
     *
     * @param enable enable
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * 获取 打开接口日志
     *
     * @return openInterfaceLog
     */
    public boolean getOpenInterfaceLog() {
        return openInterfaceLog;
    }

    /**
     * 判断是否开启接口日志
     *
     * @return openInterfaceLog
     */
    public boolean isOpenInterfaceLog() {
        return openInterfaceLog;
    }

    /**
     * 设置 打开接口日志
     *
     * @param openInterfaceLog 打开接口日志
     */
    public void setOpenInterfaceLog(boolean openInterfaceLog) {
        this.openInterfaceLog = openInterfaceLog;
    }

    /**
     * 获取慢请求阈值（毫秒）
     *
     * @return slowRequestThresholdMs
     */
    public long getSlowRequestThresholdMs() {
        return slowRequestThresholdMs;
    }

    /**
     * 设置慢请求阈值（毫秒）
     *
     * @param slowRequestThresholdMs 阈值
     */
    public void setSlowRequestThresholdMs(long slowRequestThresholdMs) {
        this.slowRequestThresholdMs = slowRequestThresholdMs;
    }
}
