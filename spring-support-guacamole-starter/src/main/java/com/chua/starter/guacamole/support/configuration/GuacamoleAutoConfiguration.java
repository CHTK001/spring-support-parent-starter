package com.chua.starter.guacamole.support.configuration;

import com.chua.starter.guacamole.support.properties.GuacamoleProperties;
import com.chua.starter.guacamole.support.service.GuacamoleService;
import com.chua.starter.guacamole.support.service.impl.GuacamoleServiceImpl;
import com.chua.starter.guacamole.support.tunnel.GuacamoleTunnelHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * Guacamole自动配置类
 *
 * @author CH
 * @since 2024/7/24
 */
@Configuration
@EnableWebSocket
@EnableConfigurationProperties(GuacamoleProperties.class)
@ConditionalOnProperty(prefix = "plugin.guacamole", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GuacamoleAutoConfiguration implements WebSocketConfigurer {

    /**
     * 注册WebSocket处理器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(guacamoleTunnelHandler(), "/guacamole/websocket-tunnel")
                .setAllowedOrigins("*")
                .withSockJS();
    }

    /**
     * Guacamole隧道处理器
     */
    @Bean
    public GuacamoleTunnelHandler guacamoleTunnelHandler() {
        return new GuacamoleTunnelHandler();
    }

    /**
     * ServerEndpoint注册器
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    /**
     * Guacamole服务
     */
    @Bean
    public GuacamoleService guacamoleService(GuacamoleProperties properties) {
        return new GuacamoleServiceImpl(properties);
    }
} 