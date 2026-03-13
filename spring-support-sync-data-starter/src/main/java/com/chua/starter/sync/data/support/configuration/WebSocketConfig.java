package com.chua.starter.sync.data.support.configuration;

import com.chua.starter.sync.data.support.properties.SyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置类
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = SyncProperties.PRE, name = "websocket-enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketConfig implements WebSocketConfigurer {

    private final SyncProperties syncProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        log.info("WebSocket配置已加载，实时推送功能已启用");
        // WebSocket Handler将在后续阶段实现
        // registry.addHandler(syncProgressHandler(), "/ws/sync/progress")
        //         .setAllowedOrigins("*");
    }
}
