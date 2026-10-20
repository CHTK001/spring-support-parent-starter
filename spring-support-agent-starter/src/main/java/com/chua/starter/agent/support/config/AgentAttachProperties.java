package com.chua.starter.agent.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 自注入配置项（前缀 {@code plugin.agent}）。
 *
 * @author CH
 * @since 4.0.0.42
 */
@ConfigurationProperties(prefix = "plugin.agent")
public class AgentAttachProperties {

    /**
     * 是否启用自注入（默认启用）。
    */
    private boolean enabled = true;

    /**
     * agent 参数（如 {@code port=4567}；空串时由 RuntimeBoot 默认）。
    */
    private String options = "";

    /**
     * Shell 端口（options 为空时作为默认 options 传入）。
    */
    private int shellPort = 4567;

    /**
     * 是否异步注入（虚拟线程执行，避免阻塞 Spring 启动）。
    */
    private boolean async = true;

    /**
     * 是否启用自注入。
     *
     * @return 是否启用的结果
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 agent 参数。
     *
     * @return agent 参数的结果
     */
    public String getOptions() {
        return options;
    }

    /**
     * 设置 agent 参数。
     *
     * @param options agent 参数
     */
    public void setOptions(String options) {
        this.options = options;
    }

    /**
     * 获取 Shell 端口。
     *
     * @return Shell端口的结果
     */
    public int getShellPort() {
        return shellPort;
    }

    /**
     * 设置 Shell 端口。
     *
     * @param shellPort Shell 端口
     */
    public void setShellPort(int shellPort) {
        this.shellPort = shellPort;
    }

    /**
     * 是否异步注入。
     *
     * @return 是否异步注入的结果
     */
    public boolean isAsync() {
        return async;
    }

    /**
     * 设置是否异步注入。
     *
     * @param async 是否异步注入
     */
    public void setAsync(boolean async) {
        this.async = async;
    }
}
