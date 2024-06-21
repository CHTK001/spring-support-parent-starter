package com.chua.starter.common.support.configuration.environment;

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
public class SwaggerEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        properties.setProperty("springdoc.api-docs.enabled", "true");
        properties.setProperty("knife4j.enable", "${plugin.swagger.enable:true}");
        properties.setProperty("knife4j.basic.enable", "true");
        properties.setProperty("knife4j.basic.username", "${plugin.swagger.username:root}");
        properties.setProperty("knife4j.basic.password", "${plugin.swagger.password:root123}");
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("knife4j", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }


}
