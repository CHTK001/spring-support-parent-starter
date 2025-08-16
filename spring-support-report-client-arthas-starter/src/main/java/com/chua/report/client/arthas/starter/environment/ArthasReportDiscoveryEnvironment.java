package com.chua.report.client.arthas.starter.environment;

import com.chua.common.support.utils.StringUtils;
import com.chua.report.client.arthas.starter.properties.ArthasClientProperties;
import com.chua.starter.discovery.support.service.DiscoveryEnvironment;
import lombok.RequiredArgsConstructor;

import java.util.Properties;

/**
 * 配置环境
 * @author CH
 */
@RequiredArgsConstructor
public class ArthasReportDiscoveryEnvironment implements DiscoveryEnvironment {

    final ArthasClientProperties arthasClientProperties;

    public static final String REPORT_ARTHAS_CLIENT_PORT = "report.client.arthas.port";
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
        properties.put(REPORT_ARTHAS_CLIENT_PORT, arthasClientProperties.getTunnelAddress());
        return properties;
    }
}
