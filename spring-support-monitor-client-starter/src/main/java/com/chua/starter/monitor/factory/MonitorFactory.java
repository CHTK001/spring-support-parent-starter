package com.chua.starter.monitor.factory;

import com.chua.starter.monitor.properties.MonitorProperties;
import lombok.Getter;
import org.springframework.core.env.Environment;

/**
 * 监视器工厂
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@Getter
public class MonitorFactory {

    private static final MonitorFactory INSTANCE = new MonitorFactory();
    private MonitorProperties monitorProperties;
    private String appName;
    private Environment environment;
    private String active;

    public static MonitorFactory getInstance() {
        return INSTANCE;
    }

    public void register(MonitorProperties monitorProperties) {
        this.monitorProperties = monitorProperties;
    }

    public void registerAppName(String appName) {
        this.appName = appName;
    }

    public void register(Environment environment) {
        this.environment = environment;
        this.active = environment.getProperty("spring.profiles.active", "default");
    }
}
