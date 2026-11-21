package com.chua.starter.monitor.support.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 监控客户端（agent 模式）配置。
 *
 * <p>宿主以 {@code plugin.monitor.mode=client} 启用时生效：周期采集本机指标并推送到
 * monitor 服务端，同时维持心跳。服务端不可达时静默降级，按重探周期自动恢复。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@ConfigurationProperties(prefix = "plugin.monitor.client")
public class MonitorClientProperties {

    /**
     * monitor 服务端地址（如 http://172.16.9.194:8082）。
     * 能力探测与心跳/指标上报都发往该地址；为空时客户端引擎不启动。
     */
    private String serverUrl;

    /**
     * 探针标识（对应服务端 ms_monitor.monitor_agent_key，请求头 X-Agent-Key）。
     * 为空时仅保留能力探测，不推送心跳与指标。
     */
    private String agentKey;

    /**
     * 指标推送间隔（秒），最小 5
     */
    private int pushIntervalSeconds = 15;

    /**
     * 心跳间隔（秒），最小 5
     */
    private int heartbeatIntervalSeconds = 30;

    /**
     * 能力探测路径：monitor 专属端点，404 视为「不支持 agent 监控」
     */
    private String probePath = "/ms/monitor/agent/status";

    /**
     * 能力重探间隔（秒）：判定不可用后按该周期重新探测，成功即恢复推送
     */
    private int reprobeIntervalSeconds = 300;

    /**
     * 连接超时（毫秒）
     */
    private int connectTimeoutMs = 3000;

    /**
     * 读超时（毫秒）
     */
    private int readTimeoutMs = 5000;
}
