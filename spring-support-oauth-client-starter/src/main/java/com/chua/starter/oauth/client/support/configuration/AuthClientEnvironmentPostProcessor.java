package com.chua.starter.oauth.client.support.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

/**
 * 身份验证客户端环境后处理器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/29
 */
public class AuthClientEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("oauth", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }
}
