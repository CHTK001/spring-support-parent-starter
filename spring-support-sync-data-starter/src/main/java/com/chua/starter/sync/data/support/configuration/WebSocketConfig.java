package com.chua.starter.sync.data.support.configuration;

import com.chua.starter.sync.data.support.properties.SyncProperties;
import com.chua.starter.sync.data.support.websocket.SyncProgressWebSocketHandler;
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

    private static final String SYNC_PROGRESS_ENDPOINT = "/ws/sync/progress";

    private final SyncProperties syncProperties;
    private final SyncProgressWebSocketHandler syncProgressWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(syncProgressWebSocketHandler, SYNC_PROGRESS_ENDPOINT)
                .setAllowedOriginPatterns("*");

        log.info("WebSocket配置已加载，实时推送功能已启用: enabled={}, endpoint={}",
                syncProperties.isWebsocketEnabled(), SYNC_PROGRESS_ENDPOINT);
    }
}
