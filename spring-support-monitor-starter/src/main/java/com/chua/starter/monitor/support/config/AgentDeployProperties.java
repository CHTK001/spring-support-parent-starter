package com.chua.starter.monitor.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 运行时接入配置
 *
 * <p>Agent 安装/卸载/升级等部署能力已下线（监控采集由 monitor 通道承担），
 * 本配置仅保留平台访问已部署 Agent 所需的运行时参数：
 * 端口、接口令牌与请求超时。</p>
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
     * agent 默认端口
    */
    private int port = DEFAULT_PORT;

    /**
     * 访问 agent /exec 等端点的接口令牌；为空表示不启用令牌校验。
     * 平台侧访问 agent 时会自动带上同值 token。
    */
    private String token = "";

    /**
     * 单次 agent HTTP 请求超时（秒）
    */
    private int requestTimeoutSeconds = 8;
}
