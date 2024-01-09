package com.chua.starter.mybatis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

/**
 * mybatis plus环境后处理器
 *
 * @author CH
 */
public class MybatisPlusEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        properties.setProperty("mybatis-plus.configuration.default-enum-type-handler", "org.apache.ibatis.type.EnumOrdinalTypeHandler");

        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("mybatis-plus-extension", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }
}
