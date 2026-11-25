package com.chua.starter.agent.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 自注入配置项（前缀 {@code plugin.agent}）。
 *
 * <p>完整 YAML 配置示例（全部项均为可选，未配置时取下方字段默认值）：</p>
 *
 * <pre>{@code
 * plugin:
 *   agent:
 *     enabled: true        # 总开关；false 时 AgentAutoConfiguration 整体不装配（默认 true）
 *     async: true          # 是否异步注入；true 在虚拟线程 agent-attach 中执行（默认 true）
 *     shell-port: 4567     # Shell 端口；options 为空时自动拼成 port=&lt;shell-port&gt; 传入 agent（默认 4567）
 *     # options: port=4567,storage.type=sqlite   # agent 参数，逗号分隔 key=value；非空时优先于 shell-port（默认空串）
 * }</pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@ConfigurationProperties(prefix = "plugin.agent")
public class AgentAttachProperties {

    /**
     * 是否启用自注入。
     *
     * <p>含义：总开关；{@code false} 时 {@code AgentAutoConfiguration} 整体不装配，
     * 不再注册 {@code AgentAttachService} 等任何 Bean，也不执行注入。</p>
     *
     * <p>默认值：{@code true}（自动配置 {@code matchIfMissing=true}，不配置等价于开启）。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>{@code
     * plugin:
     *   agent:
     *     enabled: true
     * }</pre>
    */
    private boolean enabled = true;

    /**
     * agent 参数（如 {@code port=4567}）。
     *
     * <p>含义：透传给 Runtime Agent 的参数，逗号分隔的 {@code key=value} 列表，
     * 可用键见 {@code RuntimeAgent}（如 {@code port}、{@code storage.type}、{@code storage.retention.ms} 等）；
     * 非空时优先于 {@link #shellPort}。</p>
     *
     * <p>默认值：空串 {@code ""}；为空白时注入侧回落拼接为 {@code port=&lt;shellPort&gt;}
     * （见 {@code AgentAttachService#doAttach()}）。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>{@code
     * plugin:
     *   agent:
     *     options: port=4567,storage.type=sqlite
     * }</pre>
    */
    private String options = "";

    /**
     * Shell 端口。
     *
     * <p>含义：Runtime Shell 监听端口；仅当 {@link #options} 为空白时生效，
     * 此时自动拼成 {@code port=&lt;shellPort&gt;} 作为 agent 参数传入。</p>
     *
     * <p>默认值：{@code 4567}。本模块未对 ≤0 配置做回落处理：
     * 配置为 ≤0 时会按原值拼入 {@code port=} 透传给 agent，实际行为由下游 RuntimeBoot 决定。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>{@code
     * plugin:
     *   agent:
     *     shell-port: 4567
     * }</pre>
    */
    // [P3C A20 豁免]
    private int shellPort = 4567;

    /**
     * 是否异步注入。
     *
     * <p>含义：{@code true} 在名为 {@code agent-attach} 的虚拟线程中执行注入，
     * 字节码 retransform 不阻塞 Spring 启动；{@code false} 在
     * {@code afterSingletonsInstantiated()} 中同步执行。</p>
     *
     * <p>默认值：{@code true}。注意：异步模式下「自注入完成」日志出现前
     * {@code isAgentActive()} 仍为 {@code false}，依赖方需处理未就绪状态。</p>
     *
     * <p>YAML 示例：</p>
     *
     * <pre>{@code
     * plugin:
     *   agent:
     *     async: true
     * }</pre>
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
