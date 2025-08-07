package com.chua.report.client.starter.environment;

import com.chua.starter.discovery.support.service.DiscoveryEnvironment;

import java.util.Properties;

/**
 * 配置环境
 * @author CH
 */
public class ReportDiscoveryEnvironment implements DiscoveryEnvironment {
    @Override
    public String getName() {
        return "report";
    }

    @Override
    public Properties getProperties() {
        Properties properties = new Properties();
        return properties;
    }
}
