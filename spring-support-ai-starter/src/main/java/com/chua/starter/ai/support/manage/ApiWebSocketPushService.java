package com.chua.starter.ai.support.manage;

import com.chua.starter.ai.support.manage.websocket.SysAiWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 前端事件推送服务（封装 {@code /ws} 广播能力）。
 *
 * <p>供 REST 控制器在业务处理后向所有在线前端推送事件（如 {@code message.stream}、
 * {@code turn.completed}、{@code conversation.listChanged} 等），前端按事件名路由。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiWebSocketPushService {

    /**
     * WS 处理器（持有在线连接与广播能力）
     */
    private final SysAiWebSocketHandler webSocketHandler;

    /**
      * 推送事件（{@code {name, data}} 信封，前端按 名称 路由）。
     *
     * @param event 事件名（如 消息.流）
     * @param data  事件数据
     */
    public void push(String event, Object data) {
        webSocketHandler.broadcast(event, data);
    }
}
