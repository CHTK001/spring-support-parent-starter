package com.chua.starter.monitor.support.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 监控客户端（agent 模式）配置。
 *
 * <p>宿主以 {@code plugin.monitor.mode=client} 启用时生效：周期采集本机指标并推送到
 * monitor 服务端，同时维持心跳。服务端不可达时静默降级，按重探周期自动恢复。</p>
 *
 * <p>完整配置示例（YAML，配置前缀 {@code plugin.monitor.client}）：</p>
 * <pre>
 * plugin:
 *   monitor:
 *     mode: client                                  # 客户端引擎总开关，缺省不装配
 *     client:
 *       server-url: http://monitor.example.com:8082 # monitor 服务端地址（必填）
 *       agent-key: your-agent-key                   # 探针标识（X-Agent-Key 请求头）
 *       push-interval-seconds: 15                   # 指标推送间隔（秒），最小 5
 *       heartbeat-interval-seconds: 30              # 心跳间隔（秒），最小 5
 *       probe-path: /ms/monitor/agent/status        # 能力探测路径
 *       reprobe-interval-seconds: 300               # 能力重探间隔（秒），最小 5
 *       connect-timeout-ms: 3000                    # 连接超时（毫秒），最小 500
 *       read-timeout-ms: 5000                       # 读超时（毫秒），最小 500
 * </pre>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@ConfigurationProperties(prefix = "plugin.monitor.client")
public class MonitorClientProperties {

    /**
     * monitor 服务端地址（如 http://172.16.9.194:8082）。
     * 能力探测与心跳/指标上报都发往该地址。
     *
     * <p>默认值：无。为空（null 或空白）时客户端引擎不启动（仅告警不调度），
     * 心跳与指标推送均不执行。</p>
     *
     * <pre>plugin.monitor.client.server-url: http://monitor.example.com:8082</pre>
     */
    private String serverUrl;

    /**
     * 探针标识（对应服务端 ms_monitor.monitor_agent_key，请求头 X-Agent-Key）。
     *
     * <p>默认值：无。为空（null 或空白）时仅保留能力探测，不推送心跳与指标。</p>
     *
     * <pre>plugin.monitor.client.agent-key: your-agent-key</pre>
     */
    private String agentKey;

    /**
     * 指标推送间隔（秒）。
     *
     * <p>默认值：15。配置小于 5（含 0 或负数）时按最小值 5 秒生效。</p>
     *
     * <pre>plugin.monitor.client.push-interval-seconds: 15</pre>
     */
    private int pushIntervalSeconds = 15;

    /**
     * 心跳间隔（秒）。
     *
     * <p>默认值：30。配置小于 5（含 0 或负数）时按最小值 5 秒生效。</p>
     *
     * <pre>plugin.monitor.client.heartbeat-interval-seconds: 30</pre>
     */
    private int heartbeatIntervalSeconds = 30;

    /**
     * 能力探测路径：monitor 专属端点，探测返回 404 视为「不支持 agent 监控」，
     * 连接失败视为「暂不可达」，其余状态（含 401/403）视为端点存在。
     *
     * <p>默认值：/ms/monitor/agent/status。无回落值：为空会导致探测 URI 非法，
     * 建议保持默认或显式配置 monitor 专属端点。</p>
     *
     * <pre>plugin.monitor.client.probe-path: /ms/monitor/agent/status</pre>
     */
    private String probePath = "/ms/monitor/agent/status";

    /**
     * 能力重探间隔（秒）：判定不可用/不支持后按该周期重新探测，成功即恢复推送；
     * 探测结论（含否定结论）在该周期内缓存，不逐轮重复探测。
     *
     * <p>默认值：300。配置小于 5（含 0 或负数）时按最小值 5 秒生效。</p>
     *
     * <pre>plugin.monitor.client.reprobe-interval-seconds: 300</pre>
     */
    private int reprobeIntervalSeconds = 300;

    /**
     * 连接超时（毫秒）。
     *
     * <p>默认值：3000。配置小于 500（含 0 或负数）时按最小值 500 毫秒生效。</p>
     *
     * <pre>plugin.monitor.client.connect-timeout-ms: 3000</pre>
     */
    private int connectTimeoutMs = 3000;

    /**
     * 读超时（毫秒）。
     *
     * <p>默认值：5000。配置小于 500（含 0 或负数）时按最小值 500 毫秒生效。</p>
     *
     * <pre>plugin.monitor.client.read-timeout-ms: 5000</pre>
     */
    private int readTimeoutMs = 5000;
}
