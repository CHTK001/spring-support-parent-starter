package com.chua.starter.datasource.configuration.environment;

import com.chua.common.support.utils.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

/**
 * datasource 默认环境配置
 *
 * @author CH
 */
public class DataSourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();

        String existingUrl = environment.getProperty("spring.datasource.url");
        if (!StringUtils.hasText(existingUrl)) {
            properties.setProperty("spring.datasource.url", "jdbc:sqlite:./sys");
            properties.setProperty("spring.datasource.driver-class-name", "org.sqlite.JDBC");
            properties.setProperty("spring.datasource.username", "sa");
            properties.setProperty("spring.datasource.password", "");
            properties.setProperty("spring.jpa.database-platform", "com.chua.hibernate.support.dialect.SQLiteDialect");
        }

        properties.setProperty("spring.jpa.hibernate.ddl-auto", "update");
        properties.setProperty("spring.jpa.show-db", "true");

        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("datasource", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 10;
    }
}
