package com.chua.starter.gateway;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "gateway.client")
public class GatewayClientProperties {

    /** 是否启用网关客户端自动注册 */
    private boolean enabled = true;

    /** 网关地址 */
    private String host = "127.0.0.1";

    /** 网关 Agent 注册端口（TCP） */
    private int port = 9001;

    /** 注册密钥，需与网关 agentRegisterKey 一致 */
    private String secret = "gateway-agent-secret";

    /** Agent ID，默认自动生成 UUID */
    private String agentId;

    /** 注册协议列表 */
    private List<String> protocols = new ArrayList<>(List.of("HTTP"));

    /** 本应用 HTTP 端口，0 表示自动检测 server.port */
    private int httpPort;

    /** 本应用 WebSocket 端口，0 表示不注册 WS */
    private int wsPort;
}
