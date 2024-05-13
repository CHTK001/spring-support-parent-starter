package com.chua.socketio.support.session;

import com.corundumstudio.socketio.SocketIOClient;

/**
 * SocketSessionTemplate 接口定义了会话管理与消息发送的方法。
 * 用于与SocketIO客户端进行交互。
 * @author CH
 */
public interface SocketSessionTemplate {

    /**
     * 保存或更新一个SocketIO客户端的会话。
     *
     * @param client SocketIO客户端实例。
     * @return 保存后的Socket会话实例。
     */
    SocketSession save(SocketIOClient client);

    /**
     * 从会话中移除指定的SocketIO客户端。
     *
     * @param client 需要被移除的SocketIO客户端实例。
     */
    void remove(SocketIOClient client);

    /**
     * 根据会话ID获取对应的Socket会话实例。
     *
     * @param sessionId 会话ID。
     * @return 对应的Socket会话实例，如果不存在则返回null。
     */
    SocketSession getSession(String sessionId);

    /**
     * 向指定会话ID的客户端发送消息。
     *
     * @param sessionId 目标客户端的会话ID。
     * @param event 触发的事件名称。
     * @param msg 要发送的消息内容。
     */
    void send(String sessionId, String event, String msg);

    /**
     * 向所有客户端发送消息。
     *
     * @param event 触发的事件名称。
     * @param msg 要发送的消息内容。
     */
    void send(String event, String msg);
}

