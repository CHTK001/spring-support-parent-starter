package com.chua.report.client.starter.jpom.agent.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author CH
 * @since 2025/6/3 9:49
 */
@Data
@ConfigurationProperties("plugin.maintenance.server.config")
public class ServerConfig {
    /**
     * 服务器地址
     */
    private String pushUrl = "http://127.0.0.1:19170/monitor/api/api/node/receive_push?token=3256febe97cb368bd715db5969dc52d0ad63f55d&workspaceId=DEFAULT";
}
