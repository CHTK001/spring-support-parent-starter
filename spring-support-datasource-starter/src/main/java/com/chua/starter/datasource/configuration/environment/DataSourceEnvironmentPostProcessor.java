package com.chua.starter.datasource.configuration.environment;

import com.chua.common.support.core.utils.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

/**
 * datasource 默认环境配置
 *
 * @author CH
 */
public class DataSourceEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        properties.setProperty("spring.datasource.url", "jdbc:sqlite:./sys");
        properties.setProperty("spring.datasource.driver-class-name", "org.sqlite.JDBC");
        properties.setProperty("spring.datasource.username", "sa");
        properties.setProperty("spring.datasource.password", "");
        properties.setProperty("spring.jpa.hibernate.ddl-auto", "update");
        properties.setProperty("spring.jpa.show-db", "true");

        String datasourceUrl = StringUtils.defaultString(environment.getProperty("spring.datasource.url"),
                properties.getProperty("spring.datasource.url"));
        if (datasourceUrl.contains("jdbc:sqlite")) {
            properties.setProperty("spring.jpa.database-platform", "com.chua.hibernate.support.dialect.SQLiteDialect");
        }

        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("datasource", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }
}
