package com.chua.starter.ai.support.manage;

import com.chua.starter.ai.support.manage.websocket.SysAiWebSocketHandler;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 前端事件推送服务（封装 {@code /ws} 的按用户投递能力）。
 *
 * <p>供 REST 控制器在业务处理后向前端推送事件（如 {@code message.stream}、
 * {@code turn.completed}、{@code conversation.listChanged} 等），前端按事件名路由。</p>
 *
 * <p>隔离口径：推送一律带归属用户。{@link #push(String, Object)} 取当前登录身份；
 * 后台线程（轮询、调度）请按资源归属显式调用 {@link #pushToUser(String, String, Object)}。
 * 两者都拿不到身份时不会广播给所有人——那等于把他人数据推给所有在线客户端。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiWebSocketPushService {

    /**
     * WS 处理器（持有在线连接与按用户投递能力）
     */
    private final SysAiWebSocketHandler webSocketHandler;

    /**
     * 以当前登录身份推送事件。
     *
     * @param event 事件名（如 消息.流）
     * @param data  事件数据
     */
    public void push(String event, Object data) {
        String userId = AiUserContext.currentUserId();
        if (userId == null) {
            log.warn("[ws推送]当前无登录身份，事件只发给未启用认证的连接: event={}", event);
            webSocketHandler.sendToUser(SysAiWebSocketHandler.AUTH_DISABLED_OWNER, event, data);
            return;
        }
        webSocketHandler.sendToUser(userId, event, data);
    }

    /**
     * 按资源归属推送事件（后台线程取不到请求身份时使用）。
     *
     * @param userId 事件归属用户标识，为空则不投递
     * @param event  事件名
     * @param data   事件数据
     */
    public void pushToUser(String userId, String event, Object data) {
        webSocketHandler.sendToUser(userId, event, data);
    }
}
