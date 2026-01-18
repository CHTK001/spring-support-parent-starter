package com.chua.report.client.arthas.starter.configuration;

import com.chua.common.support.network.protocol.server.ProtocolServer;
import com.chua.common.support.core.utils.StringUtils;
import com.chua.report.client.arthas.starter.properties.ArthasClientProperties;
import com.taobao.arthas.agent.attach.ArthasAgent;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;

/**
 * 自我注入：
 * 1) 若启用则向服务端上报 Arthas 配置
 * 2) 若存在协议服务，则将本模块映射注册到 ObjectContext
 */
public class ArthasConfigReporter implements InitializingBean {

    private final ArthasClientProperties arthasClientProperties;
    private final ObjectProvider<ProtocolServer> protocolServerProvider;
    private final Environment environment;

    public ArthasConfigReporter(ArthasClientProperties arthasClientProperties,
                                ObjectProvider<ProtocolServer> protocolServerProvider, Environment environment) {
        this.arthasClientProperties = arthasClientProperties;
        this.protocolServerProvider = protocolServerProvider;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (!arthasClientProperties.isEnable()) {
            return;
        }
        String tunnelAddress = arthasClientProperties.getTunnelAddress();
        // 使用 ArthasAgent 自我注入
        LinkedHashMap<String, String> config = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(tunnelAddress)) {
            config.put("arthas.tunnelServer", tunnelAddress);
        }
        // 允许使用 spring.application.name 作为 appName（若存在）
        String nodeId = environment.getProperty("spring.application.name", "unknown");
        String appName = environment.resolvePlaceholders(arthasClientProperties.getAgentName() != null ? arthasClientProperties.getAgentName() : "${spring.application.name:" + nodeId + "}");
        String agentId = environment.resolvePlaceholders(arthasClientProperties.getAgentId() != null ? arthasClientProperties.getAgentId() : nodeId);
        config.put("arthas.appName", appName);
        config.put("arthas.agentId", agentId);
        // 默认启用 telnet/http 端口，保留 Arthas 默认值即可
        try {
            ArthasAgent.attach(config);
        } catch (Throwable ignored) {
        }

        ProtocolServer protocolServer = protocolServerProvider.getIfAvailable();
        if (protocolServer != null) {
            protocolServer.getObjectContext().registerMapping(new ArthasMappingConfiguration(arthasClientProperties));
        }
    }
}


