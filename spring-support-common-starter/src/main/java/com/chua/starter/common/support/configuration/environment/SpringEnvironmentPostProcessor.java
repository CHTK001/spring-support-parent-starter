package com.chua.starter.common.support.configuration.environment;

import com.chua.starter.common.support.utils.IpUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.util.Properties;

/**
 * spring环境后处理器
 *
 * @author CH
 */
@SuppressWarnings("removal")
public class SpringEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    /** Post处理Environment */
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties properties = new Properties();

        properties.setProperty("server.compression.enabled", "true");
        properties.setProperty("server.http2.enabled", "true");
        properties.setProperty("spring.output.ansi.enabled", "always");
        properties.setProperty("server.max-http-header-size", "67108864");
        properties.setProperty("logging.level.org.zbus.net.tcp.TcpClient", "OFF");
        properties.setProperty("spring.web.resources.add-mappings", "false");
        properties.setProperty("spring.servlet.multipart.enabled", "true");
        properties.setProperty("spring.servlet.multipart.max-file-size", "600MB");
        properties.setProperty("spring.servlet.multipart.max-request-size", "2000MB");
        properties.setProperty("localhost.address", IpUtils.getLocalIpv4());

        PropertiesPropertySource propertiesPropertySource = new PropertiesPropertySource("spring", properties);
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.addLast(propertiesPropertySource);
    }
}
