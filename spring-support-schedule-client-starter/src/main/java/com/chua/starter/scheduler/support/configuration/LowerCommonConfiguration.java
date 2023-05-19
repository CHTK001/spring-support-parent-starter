package com.chua.starter.scheduler.support.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

/**
 * config
 * @author CH
 */
public class LowerCommonConfiguration implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        // Whether to enable PowerJob Worker, default is true
        properties.setProperty("powerjob.worker.enabled", "true");
        //Turn on test mode and do not force the server connection to be verified
        properties.setProperty("powerjob.worker.enable-test-mode", "false");
        //# Transport port, default is 27777
        properties.setProperty("powerjob.worker.port", "27777");
        //Application name, used for grouping applications. Recommend to set the same value as project name.
        properties.setProperty("powerjob.worker.app-name", environment.getProperty("spring.application.name"));
        //# Address of PowerJob-server node(s). Ip:port or domain. Multiple addresses should be separated with comma.
        properties.setProperty("powerjob.worker.server-address", "127.0.0.1:7700");
        //# transport protocol between server and worker
        properties.setProperty("powerjob.worker.protocol", "http");
        //# Store strategy of H2 database. disk or memory. Default value is disk.
        properties.setProperty("powerjob.worker.store-strategy", "disk");
        //# Max length of result. Results that are longer than the value will be truncated.
        properties.setProperty("powerjob.worker.max-result-length", "4096");
        //# Max length of appended workflow context . Appended workflow context value that is longer than the value will be ignore.
        properties.setProperty("powerjob.worker.max-appended-wf-context-length", "4096");
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("schedule", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }
}
