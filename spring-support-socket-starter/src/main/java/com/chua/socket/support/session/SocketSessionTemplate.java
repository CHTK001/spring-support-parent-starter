package com.chua.socket.support.session;

import com.chua.socket.support.util.Json;
import com.chua.socket.support.MsgEvent;
import com.chua.socket.support.MsgStep;

import java.util.List;
import java.util.Map;

/**
 * Socket 会话操作模板接口
 * 定义了会话管理与消息发送的方法
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
public interface SocketSessionTemplate {

    /**
     * 保存或更新一个客户端的会话
     *
     * @param clientId 客户端唯一标识符
     * @param session  会话实例
     * @return 保存后的会话实例
     */
    SocketSession save(String clientId, SocketSession session);

    /**
     * 从会话中移除指定的客户端
     *
     * @param clientId 客户端唯一标识符
     * @param session  需要被移除的会话实例
     */
    void remove(String clientId, SocketSession session);

    /**
     * 根据会话ID获取对应的会话实例
     *
     * @param sessionId 会话唯一标识符
     * @return 对应的会话实例，如果不存在则返回null
     */
    SocketSession getSession(String sessionId);

    /**
     * 向指定会话ID的客户端发送消息
     *
     * @param sessionId 目标客户端的会话ID
     * @param event     触发的事件名称
     * @param msg       要发送的消息内容
     */
    void send(String sessionId, String event, String msg);

    /**
     * 向指定所有会话的客户端发送消息
     *
     * @param event     触发的事件名称
     * @param msg       要发送的消息内容
     */
    default void send(String event, Object msg) {
        broadcastObject(event, msg);
    }

    /**
     * 向指定所有会话的客户端发送消息
     *
     * @param event     触发的事件名称
     * @param msg       要发送的消息内容
     */
    default void sendObject(String event, Object msg) {
        broadcastObject(event, msg);
    }
    /**
     * 向指定会话ID的客户端发送对象消息
     *
     * @param sessionId 目标客户端的会话ID
     * @param event     触发的事件名称
     * @param msg       要发送的消息内容对象
     */
    default void sendObject(String sessionId, String event, Object msg) {
        send(sessionId, event, msg instanceof String ? msg.toString() : Json.toJson(msg));
    }

    /**
     * 广播消息到所有客户端
     *
     * @param event 触发的事件名称
     * @param msg   要发送的消息内容
     */
    void broadcast(String event, String msg);

    /**
     * 广播消息到所有客户端
     *
     * @param event 触发的事件名称
     * @param msg   要发送的消息内容对象
     */
    default void broadcastObject(String event, Object msg) {
        if (msg instanceof MsgEvent message) {
            broadcast(event, message);
            return;
        }
        if (msg instanceof MsgStep message) {
            broadcast(event, message);
            return;
        }
        broadcast(event, msg instanceof String ? msg.toString() : Json.toJson(msg));
    }

    /**
     * 广播Map消息到所有客户端
     *
     * @param event    触发的事件名称
     * @param msgEvent 消息Map
     */
    default void broadcast(String event, Map<String, Object> msgEvent) {
        broadcast(event, Json.toJSONString(msgEvent));
    }

    /**
     * 广播MsgEvent消息到所有客户端
     *
     * @param event    触发的事件名称
     * @param msgEvent 消息事件对象
     */
    default void broadcast(String event, MsgEvent msgEvent) {
        broadcast(event, msgEvent.toString());
    }

    /**
     * 广播MsgStep消息到所有客户端
     *
     * @param event   触发的事件名称
     * @param msgStep 消息步骤对象
     */
    default void broadcast(String event, MsgStep msgStep) {
        broadcast(event, msgStep.toString());
    }

    /**
     * 向指定用户发送消息
     *
     * @param userId 目标用户ID
     * @param event  触发的事件名称
     * @param msg    要发送的消息内容
     */
    void sendToUser(String userId, String event, String msg);

    /**
     * 向指定用户发送对象消息
     *
     * @param userId 目标用户ID
     * @param event  触发的事件名称
     * @param msg    要发送的消息内容对象
     */
    default void sendToUserObject(String userId, String event, Object msg) {
        sendToUser(userId, event, msg instanceof String ? msg.toString() : Json.toJson(msg));
    }

    /**
     * 获取所有在线会话列表
     *
     * @return 在线会话列表
     */
    List<SocketSession> getOnlineSessions();

    /**
     * 获取指定类型和房间ID的在线会话列表
     *
     * @param type   类型（如 webrtc、chat 等）
     * @param roomId 房间ID
     * @return 在线会话列表
     */
    List<SocketSession> getOnlineSession(String type, String roomId);

    /**
     * 获取指定用户类型的在线用户列表
     *
     * @param type 用户类型
     * @return 在线用户列表
     */
    List<SocketUser> getOnlineUsers(String type);

    /**
     * 获取在线会话数量
     *
     * @return 在线会话数量
     */
    int getOnlineCount();

    /**
     * 向指定会话ID的客户端发送二进制数据
     *
     * @param sessionId 目标客户端的会话ID
     * @param event     触发的事件名称
     * @param data      二进制数据
     */
    default void sendBinary(String sessionId, String event, byte[] data) {
        SocketSession session = getSession(sessionId);
        if (session != null) {
            session.sendBinary(event, data);
        }
    }

    /**
     * 广播二进制数据到所有客户端
     *
     * @param event 触发的事件名称
     * @param data  二进制数据
     */
    default void broadcastBinary(String event, byte[] data) {
        List<SocketSession> sessions = getOnlineSessions();
        for (SocketSession session : sessions) {
            try {
                session.sendBinary(event, data);
            } catch (Exception e) {
                // 忽略单个会话发送失败
            }
        }
    }

    /**
     * 向指定用户发送二进制数据
     *
     * @param userId 目标用户ID
     * @param event  触发的事件名称
     * @param data   二进制数据
     */
    default void sendBinaryToUser(String userId, String event, byte[] data) {
        List<SocketSession> sessions = getOnlineSessions();
        for (SocketSession session : sessions) {
            SocketUser user = session.getUser();
            if (user != null && userId.equals(user.getUserId())) {
                try {
                    session.sendBinary(event, data);
                } catch (Exception e) {
                    // 忽略单个会话发送失败
                }
            }
        }
    }

    /**
     * 启动服务
     */
    void start();

    /**
     * 停止服务
     */
    void stop();
}
