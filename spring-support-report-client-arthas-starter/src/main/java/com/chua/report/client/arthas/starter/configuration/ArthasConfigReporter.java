package com.chua.report.client.arthas.starter.configuration;

import com.chua.common.support.protocol.server.ProtocolServer;
import com.chua.common.support.utils.StringUtils;
import com.chua.report.client.arthas.starter.properties.ArthasClientProperties;
import com.chua.report.client.starter.setting.SettingFactory;
import com.taobao.arthas.agent.attach.ArthasAgent;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;

/**
 * 自我注入：
 * 1) 若启用则向服务端上报 Arthas 配置
 * 2) 若存在协议服务，则将本模块映射注册到 ObjectContext
 */
public class ArthasConfigReporter implements InitializingBean {

    private final ArthasClientProperties arthasClientProperties;
    private final ObjectProvider<ProtocolServer> protocolServerProvider;

    public ArthasConfigReporter(ArthasClientProperties arthasClientProperties,
                                ObjectProvider<ProtocolServer> protocolServerProvider) {
        this.arthasClientProperties = arthasClientProperties;
        this.protocolServerProvider = protocolServerProvider;
    }

    @Override
    public void afterPropertiesSet() {
        if (!arthasClientProperties.isEnable()) {
            return;
        }
        if (!SettingFactory.getInstance().isEnable()) {
            return;
        }
        String tunnelAddress = arthasClientProperties.getTunnelAddress();
        // 使用 ArthasAgent 自我注入
        LinkedHashMap<String, String> config = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(tunnelAddress)) {
            config.put("tunnelServer", tunnelAddress);
        }
        // 允许使用 spring.application.name 作为 appName（若存在）
        String appName = System.getProperty("spring.application.name");
        if (StringUtils.isNotBlank(appName)) {
            config.put("appName", appName);
        }
        config.put("agentId", arthasClientProperties.getAgentId());
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


