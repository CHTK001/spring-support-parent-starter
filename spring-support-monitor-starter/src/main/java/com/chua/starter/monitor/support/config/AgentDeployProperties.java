package com.chua.starter.monitor.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 运行时接入配置
 *
 * <p>Agent 安装/卸载/升级等部署能力已下线（监控采集由 monitor 通道承担），
 * 本配置仅保留平台访问已部署 Agent 所需的运行时参数：
 * 端口、接口令牌与请求超时。历史版本中的 {@code jar-path}、{@code jar-url}、
 * {@code *-service-name}、{@code remote-dir-name}、{@code health-wait-seconds}、
 * {@code java-command}、{@code report-url}、{@code apm-*} 等配置项已移除，配置不再生效。</p>
 *
 * <p>完整配置示例（前缀 {@code plugin.monitor.agent}，值均为默认值）：</p>
 * <pre>
 * plugin:
 *   monitor:
 *     agent:
 *       port: 19876                  # agent 默认 HTTP 端口（主机未单独配置时使用）
 *       token: change-me             # 访问 agent /exec 等端点的接口令牌；留空表示不启用令牌校验
 *       request-timeout-seconds: 8   # 单次 agent HTTP 请求超时（秒）
 *       tcp-port: 19390              # Agent TCP 上报端口（@Value 注入 AgentTcpServerConfiguration，不在本类）
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Component
@ConfigurationProperties(prefix = "plugin.monitor.agent")
public class AgentDeployProperties {

    /**
     * agent 默认 HTTP 端口（主机未单独配置时使用）
    */
    public static final int DEFAULT_PORT = 19876;

    /**
     * agent 默认端口。
     *
     * <p>默认值：{@code 19876}（即 {@link #DEFAULT_PORT}）。
     * 平台访问侧的取值回落链：主机级配置 {@code ms_server_host_agent_port} 优先；
     * 本项配置 &le;0 或 &ge;65536 时回落到 {@link #DEFAULT_PORT}
     * （见 {@code AgentHttpClient#portOf}）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     agent:
     *       port: 19876
     * </pre>
     */
    private int port = DEFAULT_PORT;

    /**
     * 访问 agent /exec 等端点的接口令牌；为空表示不启用令牌校验。
     * 平台侧访问 agent 时会自动带上同值 token。
     *
     * <p>默认值：空字符串（{@code ""}），即不启用令牌校验；生产环境建议改为长随机串。
     * 平台侧仅在请求 {@code /exec} 端点时以查询参数 {@code token} 携带该值
     * （见 {@code AgentHttpClient#buildUrl}）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     agent:
     *       token: change-me
     * </pre>
     */
    private String token = "";

    /**
     * 单次 agent HTTP 请求超时（秒）。
     *
     * <p>默认值：{@code 8}；配置 &le;0 时平台访问侧按 {@code 8} 秒处理
     * （见 {@code AgentHttpClient#timeoutMillis}）。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   monitor:
     *     agent:
     *       request-timeout-seconds: 8
     * </pre>
     */
    private int requestTimeoutSeconds = 8;
}
