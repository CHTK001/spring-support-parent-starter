package com.chua.report.client.arthas.starter.environment;

import com.chua.common.support.core.utils.StringUtils;
import com.chua.report.client.arthas.starter.properties.ArthasClientProperties;
import com.chua.starter.discovery.support.service.DiscoveryEnvironment;
import org.springframework.core.env.Environment;

import java.util.Properties;

/**
 * 配置环境
 * @author CH
 */
public class ArthasReportDiscoveryEnvironment implements DiscoveryEnvironment {

    private final ArthasClientProperties arthasClientProperties;
    private final Environment environment;

    /**
     * 构造器
     *
     * @param arthasClientProperties Arthas 客户端配置
     * @param environment Spring 环境
     */
    public ArthasReportDiscoveryEnvironment(ArthasClientProperties arthasClientProperties, Environment environment) {
        this.arthasClientProperties = arthasClientProperties;
        this.environment = environment;
    }

    public static final String REPORT_ARTHAS_CLIENT_PORT = "report.client.arthas.port";
    public static final String REPORT_ARTHAS_CLIENT_ID = "report.client.arthas.id";
    @Override
    public String getName() {
        return "monitor";
    }

    @Override
    public Properties getProperties() {
        Properties properties = new Properties();
        if (StringUtils.isEmpty(arthasClientProperties.getTunnelAddress())) {
            return properties;
        }
        String nodeId = environment.getProperty("spring.application.name", "unknown");
        String agentId = arthasClientProperties.getAgentId() != null ? 
                environment.resolvePlaceholders(arthasClientProperties.getAgentId()) : nodeId;
        String tunnelAddress = arthasClientProperties.getTunnelAddress() != null ? 
                environment.resolvePlaceholders(arthasClientProperties.getTunnelAddress()) : "";
        properties.put(REPORT_ARTHAS_CLIENT_ID, agentId);
        properties.put(REPORT_ARTHAS_CLIENT_PORT, tunnelAddress);
        return properties;
    }
}
