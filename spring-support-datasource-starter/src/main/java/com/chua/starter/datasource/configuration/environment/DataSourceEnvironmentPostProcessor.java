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
    /** Post处理Environment */
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

        // 数据库脚本迁移默认全部交给自研 FlywayLike（plugin.datasource.script）：
        // 关闭 spring.sql.init（schema-locations 手工清单），由 DataSourceScriptConfiguration
        // 启动时自动扫描 classpath*:db/init|migration|sync 下 V*__*.sql 执行。
        // 仅在用户未显式设置时写入默认值，避免覆盖应用自定义配置。
        if (!environment.containsProperty("spring.sql.init.mode")) {
            properties.setProperty("spring.sql.init.mode", "never");
        }
        if (!environment.containsProperty("plugin.datasource.script.enable")) {
            properties.setProperty("plugin.datasource.script.enable", "true");
        }

        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("datasource", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }

    @Override
    /** 获取Order */
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 10;
    }
}
