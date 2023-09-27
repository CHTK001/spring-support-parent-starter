package com.chua.starter.common.support.configuration;

import com.chua.common.support.function.Joiner;
import com.chua.common.support.net.NetUtils;
import com.chua.common.support.utils.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
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
        properties.setProperty("server.compression.enabled", "true");
//        properties.setProperty("spring.datasource.url", "jdbc:sqlite:./sys");
//        properties.setProperty("spring.datasource.driver-class-name", "org.sqlite.JDBC");
        properties.setProperty("spring.datasource.url", "jdbc:h2:./sys");
        properties.setProperty("spring.datasource.driver-class-name", "org.h2.Driver");
        properties.setProperty("spring.datasource.username", "sa");
        properties.setProperty("spring.datasource.password", "");
        properties.setProperty("logging.level.org.zbus.net.tcp.TcpClient", "OFF");
//        properties.setProperty("spring.datasource.h2.console.enabled", "true");
//        properties.setProperty("spring.datasource.h2.console.path", "/h2-console");

        String property1 = StringUtils.defaultString(environment.getProperty("spring.datasource.url"), properties.getProperty("spring.datasource.url"));
        if (null != property1 && property1.contains("jdbc:sqlite")) {
            properties.setProperty("spring.jpa.database-platform", "com.chua.hibernate.support.dialect.SQLiteDialect");
        }
        properties.setProperty("spring.jpa.hibernate.ddl-auto", "update");
        properties.setProperty("spring.jpa.show-sql", "true");

        properties.setProperty("spring.main.allow-circular-references", "true");
        properties.setProperty("server.compression.enable", "true");

        properties.setProperty("spring.servlet.multipart.enabled", "true");
        properties.setProperty("spring.servlet.multipart.max-file-size", "600MB");
        properties.setProperty("spring.servlet.multipart.max-request-size", "2000MB");

        properties.setProperty("localhost.address", NetUtils.getLocalIpv4());
        String[] property = environment.getProperty("plugin.auto.table.scan", String[].class);
        if(null != property) {
            List<String> oss = new LinkedList<>(Arrays.asList(property));
            oss.add("com.chua.starter.oss.support.pojo");
            oss.add("com.chua.starter.task.support.pojo");
            properties.setProperty("plugin.auto.table.scan", Joiner.on(',').join(oss));
        }
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("common", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }


}
