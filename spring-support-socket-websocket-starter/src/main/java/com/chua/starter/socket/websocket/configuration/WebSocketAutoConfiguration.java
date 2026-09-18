package com.chua.starter.socket.websocket.configuration;

import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import com.chua.starter.socket.websocket.handler.DelegatingWebSocketHandler;
import com.chua.starter.socket.websocket.handler.WebSocketMessageHandler;
import com.chua.starter.socket.websocket.properties.WebSocketProperties;
import com.chua.starter.socket.websocket.session.WebSocketSessionManager;
import com.chua.starter.socket.websocket.template.WebSocketTemplate;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
* web套接字 自动配置
* <p>
* 基于 Spring Boot 原生 web套接字，与应用同端口运行
* </p>
*
* @since 2025-12-25
* @author CH
 */
@Slf4j
@Configuration
@EnableWebSocket
@EnableConfigurationProperties(WebSocketProperties.class)
@ConditionalOnProperty(prefix = WebSocketProperties.PREFIX, name = "enable", havingValue = "true")
public class WebSocketAutoConfiguration implements WebSocketConfigurer {

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(WebSocketAutoConfiguration.class);

    @Autowired
    /** 属性 */
    private WebSocketProperties properties;

    @Autowired(required = false)
    /** 处理器 */
    private List<WebSocketMessageHandler> handlers = Collections.emptyList();

    @Bean
    @ConditionalOnMissingBean
    /**
    * web套接字会话管理器
    *
    * @return web套接字会话管理器的结果
    */
    public WebSocketSessionManager webSocketSessionManager() {
        /**
        * web套接字会话管理器。
        * @return web套接字会话管理器的结果
        */
        return new WebSocketSessionManager();
    }

    @Bean
    @ConditionalOnMissingBean
    /**
    * web套接字template
    *
    * @param sessionManager 会话管理器
    * @return web套接字template的结果
    */
    public WebSocketTemplate webSocketTemplate(WebSocketSessionManager sessionManager) {
        /**
        * web套接字template。
        * @param sessionManager 会话管理器
        * @return web套接字template的结果
        */
        return new WebSocketTemplate(sessionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    /**
    * delegatingweb套接字处理器
    *
    * @param sessionManager 会话管理器
    * @return delegatingweb套接字处理器的结果
    */
    public DelegatingWebSocketHandler delegatingWebSocketHandler(WebSocketSessionManager sessionManager) {
        /**
        * delegatingweb套接字处理器。
        * @param sessionManager 会话管理器
        * @param handlers 处理器
        * @return delegatingweb套接字处理器的结果
        */
        return new DelegatingWebSocketHandler(sessionManager, handlers);
    }

    @Bean
    /**
    * 创建web套接字容器
    *
    * @return 创建web套接字容器的结果
    */
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(properties.getMaxMessageSize());
        container.setMaxBinaryMessageBufferSize(properties.getMaxMessageSize());
        container.setMaxSessionIdleTimeout(properties.getSendTimeLimit() * 2L);
        return container;
    }

    @Override
    /** 注册web套接字处理器 */
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        WebSocketSessionManager sessionManager = webSocketSessionManager();
        DelegatingWebSocketHandler handler = delegatingWebSocketHandler(sessionManager);
        
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

    @Bean
    /**
    * web套接字服务信息contributor
    *
    * @return web套接字服务信息contributor的结果
    */
    public ServiceInfoContributor webSocketServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("websocket", "WebSocket", true, "WebSocket模块");
        };
    }
}
