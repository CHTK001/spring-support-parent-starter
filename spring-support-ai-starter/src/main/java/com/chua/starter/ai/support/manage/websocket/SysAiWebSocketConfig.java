package com.chua.starter.ai.support.manage.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
* Nomifun /ws webSocket 配置。
*
* <p>注册 {@code /ws} 端点，供前端桌面壳模式建立流式/推送连接。
* 子协议协商通过 {@link SysAiWebSocketHandler#getSubProtocols()} 声明，
* 支持桌面模式 ({@code nomi-local-trust}) 和 Web 模式 ({@code chat})。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class SysAiWebSocketConfig implements WebSocketConfigurer {

    /**
    * WS 处理器（已通过 sub协议capable 声明支持的子协议）
    */
    private final SysAiWebSocketHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .setAllowedOriginPatterns("*");
    }
}
