package com.chua.starter.rsocket.support.session;

import com.chua.common.support.json.Json;

/**
 * RSocket会话模板接口
 * <p>
 * 提供RSocket会话管理和消息发送的统一接口
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
public interface RSocketSessionTemplate {

    /**
     * 保存或更新会话
     * 
     * @param sessionId 会话ID
     * @param session   会话实例
     * @return 保存后的会话实例
     */
    RSocketSession save(String sessionId, RSocketSession session);

    /**
     * 移除会话
     * 
     * @param sessionId 会话ID
     */
    void remove(String sessionId);

    /**
     * 获取会话
     * 
     * @param sessionId 会话ID
     * @return 会话实例，不存在则返回null
     */
    RSocketSession getSession(String sessionId);

    /**
     * 向指定会话发送消息
     * 
     * @param sessionId 会话ID
     * @param event     事件名称
     * @param msg       消息内容
     */
    void send(String sessionId, String event, String msg);

    /**
     * 向指定会话发送对象消息
     * 
     * @param sessionId 会话ID
     * @param event     事件名称
     * @param msg       消息对象
     */
    default void sendObject(String sessionId, String event, Object msg) {
        send(sessionId, event, msg instanceof String ? msg.toString() : Json.toJson(msg));
    }

    /**
     * 向所有会话广播消息
     * 
     * @param event 事件名称
     * @param msg   消息内容
     */
    void broadcast(String event, String msg);

    /**
     * 向所有会话广播对象消息
     * 
     * @param event 事件名称
     * @param msg   消息对象
     */
    default void broadcastObject(String event, Object msg) {
        broadcast(event, msg instanceof String ? msg.toString() : Json.toJson(msg));
    }

    /**
     * 向指定用户发送消息
     * 
     * @param userId 用户ID
     * @param event  事件名称
     * @param msg    消息内容
     */
    void sendToUser(String userId, String event, String msg);

    /**
     * 向指定用户发送对象消息
     * 
     * @param userId 用户ID
     * @param event  事件名称
     * @param msg    消息对象
     */
    default void sendToUserObject(String userId, String event, Object msg) {
        sendToUser(userId, event, msg instanceof String ? msg.toString() : Json.toJson(msg));
    }

    /**
     * 获取所有在线会话数量
     * 
     * @return 在线会话数量
     */
    int getOnlineCount();

    /**
     * 断开指定会话
     * 
     * @param sessionId 会话ID
     */
    void disconnect(String sessionId);
}

