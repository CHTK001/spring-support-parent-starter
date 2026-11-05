// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
 * @since 4.0.0
 */
public class DataSourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    @Override
    /**
     * Post处理环境
     * @param application 参数
     * @param environment 参数
     * @return 返回值
    */
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

 // 数据库脚本迁移默认全部交给自研 flywaylike（plugin.datasource.script）：
 // 关闭 spring.SQL.初始化（模式-位置 手工清单），由 数据源script配置
 // 启动时自动扫描 类路径*:db/初始化|migration|同步 下 V*__*.SQL 执行。
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
    /**
     * 获取订单
     * @return 返回值
    */
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 10;
    }
}