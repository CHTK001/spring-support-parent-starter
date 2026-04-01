package com.chua.starter.common.support.configuration.environment;

import com.chua.common.support.network.net.NetUtils;
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
public class SpringEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();
        properties.setProperty("server.compression.enabled", "true");
        properties.setProperty("spring.output.ansi.enabled", "always");
        properties.setProperty("server.http2.enabled", "true");
        properties.setProperty("server.max-http-header-size", "67108864");
        properties.setProperty("logging.level.org.zbus.net.tcp.TcpClient", "OFF");

        properties.setProperty("spring.web.resources.add-mappings", "false");

        properties.setProperty("spring.main.allow-circular-references", "true");

        properties.setProperty("spring.servlet.multipart.enabled", "true");
        properties.setProperty("spring.servlet.multipart.max-file-size", "600MB");
        properties.setProperty("spring.servlet.multipart.max-request-size", "2000MB");

        properties.setProperty("localhost.address", NetUtils.getLocalIpv4());
        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("spring", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }


}
