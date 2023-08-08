package com.chua.starter.scheduler.client.support.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * freemarker
 *
 * @author CH
 */
public class SchedulerConfigurationConfiguration implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("plugin.scheduler.executor.app-name", environment.getProperty("spring.application.name"));
        MapPropertySource propertiesPropertySource = new MapPropertySource("scheduler-client", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }


}
