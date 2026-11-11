package com.chua.socket.support.session;

import static com.chua.starter.common.support.text.json.Json.toJson;
import static com.chua.starter.common.support.text.json.Json.toJSONString;

import com.chua.socket.support.MsgEvent;
import com.chua.socket.support.MsgStep;

import java.util.List;
import java.util.Map;

/**
 * Socket 会话操作模板接口
 * 定义了会话管理与消息发送的方法
 *
 * @since 2024-12-04
 * @author CH
 */
public interface SocketSessionTemplate {

    /**
     * 握手数据中的请求头映射键（值类型为 {@code Map<String, String>}）
     */
    String HANDSHAKE_HEADERS = "headers";

    /**
     * 握手数据中的查询参数映射键（值类型为 {@code Map<String, List<String>>}）
     */
    String HANDSHAKE_QUERY = "query";

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
     * 根据会话标识获取对应的会话实例
     *
     * @param sessionId 会话唯一标识符
     * @return 对应的会话实例，如果不存在则返回null
     */
    SocketSession getSession(String sessionId);

    /**
     * 向指定会话标识的客户端发送消息
     *
     * @param sessionId 目标客户端的会话标识
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
        /**
         * broadcast对象。
         * @param event 事件
         * @param msg msg
         */
        broadcastObject(event, msg);
    }

    /**
     * 向指定所有会话的客户端发送消息
     *
     * @param event     触发的事件名称
     * @param msg       要发送的消息内容
     */
    default void sendObject(String event, Object msg) {
        /**
         * broadcast对象。
         * @param event 事件
         * @param msg msg
         */
        broadcastObject(event, msg);
    }
    /**
     * 向指定会话标识的客户端发送对象消息
     *
     * @param sessionId 目标客户端的会话标识
     * @param event     触发的事件名称
     * @param msg       要发送的消息内容对象
     */
    default void sendObject(String sessionId, String event, Object msg) {
        /**
         * 发送。
         * @param sessionId 会话标识
         * @param event 事件
         * @param msg.toString( msg.转为字符串(
         */
        send(sessionId, event, msg instanceof String ? msg.toString() : toJson(msg));
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
            /**
             * broadcast。
             * @param event 事件
             * @param message 消息
             */
            broadcast(event, message);
            return;
        }
        if (msg instanceof MsgStep message) {
            /**
             * broadcast。
             * @param event 事件
             * @param message 消息
             */
            broadcast(event, message);
            return;
        }
        /**
         * broadcast。
         * @param event 事件
         * @param msg.toString( msg.转为字符串(
         */
        broadcast(event, msg instanceof String ? msg.toString() : toJson(msg));
    }

    /**
     * 广播映射消息到所有客户端
     *
     * @param event    触发的事件名称
     * @param msgEvent 消息映射
     */
    default void broadcast(String event, Map<String, Object> msgEvent) {
        /**
         * broadcast。
         * @param event 事件
         * @param toJSONString(msgEvent 转为jsonstring(msg事件
         */
        broadcast(event, toJSONString(msgEvent));
    }

    /**
     * 广播msg事件消息到所有客户端
     *
     * @param event    触发的事件名称
     * @param msgEvent 消息事件对象
     */
    default void broadcast(String event, MsgEvent msgEvent) {
        /**
         * broadcast。
         * @param event 事件
         * @param msgEvent.toString( msg事件.转为字符串(
         */
        broadcast(event, msgEvent.toString());
    }

    /**
     * 广播msgstep消息到所有客户端
     *
     * @param event   触发的事件名称
     * @param msgStep 消息步骤对象
     */
    default void broadcast(String event, MsgStep msgStep) {
        /**
         * broadcast。
         * @param event 事件
         * @param msgStep.toString( msgstep.转为字符串(
         */
        broadcast(event, msgStep.toString());
    }

    /**
     * 向指定用户发送消息
     *
     * @param userId 目标用户标识
     * @param event  触发的事件名称
     * @param msg    要发送的消息内容
     */
    void sendToUser(String userId, String event, String msg);

    /**
     * 向指定用户发送对象消息
     *
     * @param userId 目标用户标识
     * @param event  触发的事件名称
     * @param msg    要发送的消息内容对象
     */
    default void sendToUserObject(String userId, String event, Object msg) {
        /**
         * 发送转为用户。
         * @param userId 用户标识
         * @param event 事件
         * @param msg.toString( msg.转为字符串(
         */
        sendToUser(userId, event, msg instanceof String ? msg.toString() : toJson(msg));
    }

    /**
     * 获取所有在线会话列表
     *
     * @return 在线会话列表
     */
    List<SocketSession> getOnlineSessions();

    /**
     * 获取指定类型和房间标识的在线会话列表
     *
     * @param type   类型（如 WebRTC、对话 等）
     * @param roomId 房间标识
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
     * 向指定会话标识的客户端发送二进制数据
     *
     * @param sessionId 目标客户端的会话标识
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
     * @param userId 目标用户标识
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
     * 服务是否已经真正监听端口。
     *
     * <p>模板 Bean 存在不等于端口绑定成功：端口被占用、配置缺失时模板仍会创建。
     * 能力接口必须据此判断，避免给前端一个永远收不到帧的“可用”套接字。</p>
     *
     * @return 已就绪返回 true
     */
    default boolean isReady() {
        return true;
    }

    /**
     * 当前模板实际使用的协议名。
     *
     * @return 协议名
     */
    default String protocol() {
        return "unknown";
    }

    /**
     * 是否支持按用户定向发送。
     *
     * <p>只有握手阶段能建立可信用户身份时才返回 true；否则调用方应继续广播，
     * 不能假设 {@code sendToUser} 一定能送达。</p>
     *
     * @return 支持定向发送返回 true
     */
    default boolean supportsUserTargeting() {
        return false;
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
