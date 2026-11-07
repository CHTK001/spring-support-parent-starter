package com.chua.starter.socket.websocket.room;

import com.chua.starter.socket.websocket.handler.WebSocketMessageHandler;
import com.chua.starter.socket.websocket.template.WebSocketTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

/**
 * 房间信令 WebSocket 处理器。
 *
 * <p>处理 `/ws` 端点上的房间信令（Hello/Signal/Heartbeat/Leave），委托给
 * {@link RoomService}，并将回执信封发送回连接。</p>
 *
 * <p>接入规则（ZestSend 协议）：
 * <ul>
 *   <li>{@code hello} - 加入房间或续接旧连接 → 回执 welcome</li>
 *   <li>{@code signal} - WebRTC offer / answer / candidate 转发给对端</li>
 *   <li>{@code heartbeat} - 心跳续期 → 回执 pong</li>
 *   <li>{@code leave} - 主动离开房间 → 回执 left，通知对端 peer-left</li>
 * </ul>
 *
 * <p>信令解析与分发已抽到 {@link RoomSignalProcessor}，与独立端口的
 * {@link RoomServer} 共用同一份实现。</p>
 *
 * @author CH
 * @since 2026-11-03
 */
@Service
public class RoomWebSocketHandler implements WebSocketMessageHandler {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketHandler.class);

    /**
     * 房间信令引擎
     */
    private final RoomService roomService;

    /**
     * 信令分发器
     */
    private final RoomSignalProcessor processor;

    /**
     * 构造器
     *
     * @param roomService 房间信令引擎
     * @param sender      信令下发模板
     */
    public RoomWebSocketHandler(RoomService roomService, WebSocketTemplate sender) {
        this.roomService = roomService;
        this.processor = new RoomSignalProcessor(roomService, sender::send);
    }

    @Override
    /**
     * 连接建立时调用
     *
     * @param session 参数
     */
    public void onConnect(WebSocketSession session) {
        log.info("[Room] 连接建立: sessionId={}", session.getId());
    }

    @Override
    /**
     * 收到消息时调用
     *
     * @param session 参数
     * @param message 参数
     */
    public void onMessage(WebSocketSession session, String message) {
        processor.onMessage(session.getId(), message);
    }

    @Override
    /**
     * 连接关闭时调用
     *
     * @param session 参数
     * @param code    参数
     * @param reason  参数
     */
    public void onClose(WebSocketSession session, int code, String reason) {
        log.info("[Room] 连接关闭: sessionId={}, code={}", session.getId(), code);
        roomService.onDisconnect(session.getId());
    }

    @Override
    /**
     * 发生错误时调用
     *
     * @param session   参数
     * @param throwable 参数
     */
    public void onError(WebSocketSession session, Throwable throwable) {
        log.error("[Room] 连接错误: sessionId={}", session.getId(), throwable);
        roomService.onDisconnect(session.getId());
    }
}
