package com.chua.starter.socket.websocket.configuration;

import com.chua.starter.socket.websocket.handler.DelegatingWebSocketHandler;
import com.chua.starter.socket.websocket.handler.WebSocketMessageHandler;
import com.chua.starter.socket.websocket.properties.WebSocketProperties;
import com.chua.starter.socket.websocket.session.WebSocketSessionManager;
import com.chua.starter.socket.websocket.template.WebSocketTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.Collections;
import java.util.List;

import static com.chua.starter.common.support.logger.ModuleLog.highlight;

/**
 * WebSocket 自动配置
 * <p>
 * 基于 Spring Boot 原生 WebSocket，与应用同端口运行
 * </p>
 *
 * @author CH
 * @since 2025-12-25
 */
@Slf4j
@Configuration
@EnableWebSocket
@EnableConfigurationProperties(WebSocketProperties.class)
@ConditionalOnProperty(prefix = WebSocketProperties.PREFIX, name = "enable", havingValue = "true")
public class WebSocketAutoConfiguration implements WebSocketConfigurer {

    @Autowired
    private WebSocketProperties properties;

    @Autowired(required = false)
    private List<WebSocketMessageHandler> handlers = Collections.emptyList();

    @Bean
    @ConditionalOnMissingBean
    public WebSocketSessionManager webSocketSessionManager() {
        return new WebSocketSessionManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebSocketTemplate webSocketTemplate(WebSocketSessionManager sessionManager) {
        return new WebSocketTemplate(sessionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public DelegatingWebSocketHandler delegatingWebSocketHandler(WebSocketSessionManager sessionManager) {
        return new DelegatingWebSocketHandler(sessionManager, handlers);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(properties.getMaxMessageSize());
        container.setMaxBinaryMessageBufferSize(properties.getMaxMessageSize());
        container.setMaxSessionIdleTimeout(properties.getSendTimeLimit() * 2L);
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        DelegatingWebSocketHandler handler = delegatingWebSocketHandler(webSocketSessionManager());
        
        for (String endpoint : properties.getEndpoints()) {
            log.info("[WebSocket] 注册端点: {}", highlight(endpoint));
            
            WebSocketHandlerRegistration registration = registry.addHandler(handler, endpoint);
            
            // 设置允许的源
            if (!properties.getAllowedOrigins().isEmpty()) {
                registration.setAllowedOrigins(properties.getAllowedOrigins().toArray(new String[0]));
            }
            
            // 启用 SockJS 支持
            if (properties.isSockJs()) {
                registration.withSockJS()
                        .setHeartbeatTime(properties.getHeartbeatInterval());
            }
        }
    }
}
