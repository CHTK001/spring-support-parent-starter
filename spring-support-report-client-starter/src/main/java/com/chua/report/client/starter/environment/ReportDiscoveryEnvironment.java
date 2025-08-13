package com.chua.report.client.starter.environment;

import com.chua.report.client.starter.properties.ReportClientProperties;
import com.chua.report.client.starter.setting.SettingFactory;
import com.chua.starter.discovery.support.service.DiscoveryEnvironment;
import lombok.RequiredArgsConstructor;

import java.util.Properties;

/**
 * 配置环境
 * @author CH
 */
@RequiredArgsConstructor
public class ReportDiscoveryEnvironment implements DiscoveryEnvironment {

    final ReportClientProperties reportClientProperties;

    public static final String REPORT_CLIENT_PORT = "report.client.receive.port";
    @Override
    public String getName() {
        return "monitor";
    }

    @Override
    public Properties getProperties() {
        Properties properties = new Properties();
        properties.put(REPORT_CLIENT_PORT, SettingFactory.getInstance().getReceivePort());
        return properties;
    }
}
