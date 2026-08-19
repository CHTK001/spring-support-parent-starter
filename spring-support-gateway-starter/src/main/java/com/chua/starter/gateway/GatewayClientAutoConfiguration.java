package com.chua.starter.gateway;

import com.chua.remote.support.agent.launch.AgentProperties;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.UUID;
/** @author CH */

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(GatewayClientProperties.class)
@ConditionalOnProperty(prefix = "gateway.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GatewayClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    /** GatewayClientAgentProperties */
    public AgentProperties gatewayClientAgentProperties(GatewayClientProperties props, Environment env) {
        AgentProperties agentProps = new AgentProperties();
        agentProps.setGatewayHost(props.getHost());
        agentProps.setGatewayPort(props.getPort());
        agentProps.setAgentSecret(props.getSecret());

        // Agent ID
        String agentId = props.getAgentId();
        if (agentId == null || agentId.isBlank()) {
            agentId = UUID.randomUUID().toString().replace("-", "");
        }
        agentProps.setAgentId(agentId);

        // 协议
        agentProps.setProtocols(String.join(",", props.getProtocols()));

        // HTTP 端口：优先用配置，否则自动检测 server.port
        int httpPort = props.getHttpPort();
        if (httpPort <= 0) {
            String serverPort = env.getProperty("server.port", "8080");
            try {
                httpPort = Integer.parseInt(serverPort);
            } catch (NumberFormatException e) {
                httpPort = 8080;
            }
        }

        // capabilities 携带 HTTP/WS 端口信息
        StringBuilder caps = new StringBuilder("http_port=").append(httpPort);
        if (props.getWsPort() > 0) {
            caps.append(",ws_port=").append(props.getWsPort());
        }
        agentProps.setCapabilities(caps.toString());

        log.info("[GatewayClient] 配置: host={}:{}, agentId={}, protocols={}, httpPort={}",
                props.getHost(), props.getPort(), agentId, props.getProtocols(), httpPort);
        return agentProps;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    /** GatewayClientAgent */
    public GatewayClientAgent gatewayClientAgent(AgentProperties gatewayClientAgentProperties) {
        return new GatewayClientAgent(gatewayClientAgentProperties);
    }

    @Bean
    /** GatewayServiceInfoContributor */
    public ServiceInfoContributor gatewayServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("gateway", "网关", true, "网关客户端模块");
        };
    }
}

