package com.chua.starter.socket.websocket.room;

/**
 * 房间信令定向下发出口。
 *
 * <p>房间引擎只关心「把这段 JSON 送到这个连接」，不关心连接是怎么来的：</p>
 * <ul>
 *   <li>与应用同端口的 Spring WebSocket（{@link RoomWebSocketHandler}）：
 *       交给 {@code WebSocketTemplate#send}，按会话 ID 路由；</li>
 *   <li>独立端口的房间节点（{@link RoomServer}）：交给节点自己的连接表，
 *       按连接 ID 直接写帧。</li>
 * </ul>
 *
 * <p>抽象成函数式接口，是为了让节点可以直接 {@code this::send} 注入，
 * 而不必为了复用房间引擎去实现整套模板。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@FunctionalInterface
public interface RoomSender {

    /**
     * 向指定连接下发一段文本消息。
     *
     * @param connectionId 连接标识（Spring 会话 ID 或节点分配的连接 ID）
     * @param message      消息内容（房间信令 JSON）
     */
    void send(String connectionId, String message);
}
