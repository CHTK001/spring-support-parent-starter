package com.chua.starter.rust.server.server;

import com.chua.starter.rust.server.properties.RustServerProperties;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.server.AbstractServletWebServerFactory;

/**
 * Rust Servlet Web Server 工厂
 * <p>
 * 实现 Spring Boot 的 ServletWebServerFactory 接口，
 * 使 Rust HTTP Server 可以无缝替代 Tomcat/Undertow。
 * </p>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Setter
public class RustServletWebServerFactory extends AbstractServletWebServerFactory {

    private final RustServerProperties rustServerProperties;
    private final ServerProperties serverProperties;

    public RustServletWebServerFactory(RustServerProperties rustServerProperties,
                                       ServerProperties serverProperties) {
        this.rustServerProperties = rustServerProperties;
        this.serverProperties = serverProperties;

        // 从 ServerProperties 同步配置
        if (serverProperties.getPort() != null) {
            setPort(serverProperties.getPort());
        }
        if (serverProperties.getAddress() != null) {
            setAddress(serverProperties.getAddress());
        }
        if (serverProperties.getServlet() != null &&
                serverProperties.getServlet().getContextPath() != null) {
            setContextPath(serverProperties.getServlet().getContextPath());
        }

        log.info("[Rust Server Factory] 初始化完成");
    }

    @Override
    public WebServer getWebServer(ServletContextInitializer... initializers) {
        log.info("[Rust Server Factory] 创建 Rust Web Server");

        int port = getPort();
        String address = getAddress() != null ? getAddress().getHostAddress() : "0.0.0.0";

        return new RustWebServer(rustServerProperties, port, address, initializers);
    }
}
