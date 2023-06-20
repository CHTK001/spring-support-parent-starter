package com.chua.starter.common.support.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

/**
 * freemarker
 *
 * @author CH
 */
public class CommonConfigurationConfiguration implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        properties.setProperty("spring.datasource.url", "jdbc:h2:〜/sys");
        properties.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
        properties.setProperty("spring.datasource.username", "sa");
        properties.setProperty("spring.datasource.password", "");
        properties.setProperty("spring.datasource.h2.console.enabled", "true");
        properties.setProperty("spring.datasource.h2.console.path", "/h2-console");

        properties.setProperty("spring.main.allow-circular-references", "true");

        properties.setProperty("spring.servlet.multipart.enabled", "true");
        properties.setProperty("spring.servlet.multipart.max-file-size", "200MB");
        properties.setProperty("spring.servlet.multipart.max-request-size", "2000MB");
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("common", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }


}