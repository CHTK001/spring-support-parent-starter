package com.chua.starter.config.server.support.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

/**
 * @author CH
 */
public class ConfigEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        properties.setProperty("plugin.configuration.isOpen", "false");
        properties.setProperty("plugin.configuration.is-open", "false");
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("config-server", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addFirst(propertiesPropertySource);
    }
}
