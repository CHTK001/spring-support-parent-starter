package com.chua.socketio.support.session;

import com.chua.common.support.json.Json;
import com.chua.socketio.support.MsgEvent;
import com.chua.socketio.support.MsgStep;
import com.chua.socketio.support.resolver.SocketSessionResolver;
import com.chua.socketio.support.server.DelegateSocketIOServer;
import com.chua.socketio.support.wrapper.WrapperConfiguration;
import com.corundumstudio.socketio.SocketIOClient;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * SocketSessionTemplate 接口定义了会话管理与消息发送的方法。
 * 用于与SocketIO客户端进行交互。
 *
 * @author CH
 * @since 2024/12/11
 */
public interface SocketSessionTemplate {

    /**
     * 保存或更新一个SocketIO客户端的会话。
     *
     * @param clientId 客户端唯一标识符，例如："client_001"
     * @param client   SocketIO客户端实例，用于与客户端通信
     * @return 保存后的Socket会话实例
     */
    SocketSession save(String clientId, SocketIOClient client);

    /**
     * 从会话中移除指定的SocketIO客户端。
     *
     * @param clientId 客户端唯一标识符，例如："client_001"
     * @param client   需要被移除的SocketIO客户端实例
     */
    void remove(String clientId, SocketIOClient client);

    /**
     * 根据会话ID获取对应的Socket会话实例。
     *
     * @param sessionId 会话唯一标识符，例如："session_abc123"
     * @return 对应的Socket会话实例，如果不存在则返回null
     */
    SocketSession getSession(String sessionId);

    /**
     * 向指定会话ID的客户端发送消息。
     *
     * @param sessionId 目标客户端的会话ID，例如："session_abc123"
     * @param event     触发的事件名称，例如："message"
     * @param msg       要发送的消息内容，例如："Hello, world!"
     */
    void send(String sessionId, String event, String msg);

    /**
     * 向指定会话ID的SocketIO客户端发送消息。
     *
     * @param sessionId 目标客户端的会话ID，例如："session_abc123"
     * @param event     触发的事件名称，例如："message"
     * @param msg       要发送的消息内容对象，包含详细消息信息
     */
    default void send(String sessionId, String event, Object msg) {
        send(sessionId, event, msg instanceof String ? msg : Json.toJson(msg));
    }
    /**
     * 向所有客户端发送消息。
     *
     * @param event 触发的事件名称，例如："broadcast"
     * @param msg   要发送的消息内容，例如："System notification"
     */
    void send(String event, String msg);

    /**
     * 向所有客户端发送消息。
     *
     * @param event 触发的事件名称，例如："broadcast"
     * @param msg   要发送的消息内容对象，包含详细消息信息
     */
    default void send(String event, Object msg) {
        send(event, msg instanceof String ? msg : Json.toJson(msg));
    }
    /**
     * 向所有客户端发送消息。
     *
     * @param event    触发的事件名称，例如："notification"
     * @param msgEvent 要发送的消息内容对象，包含详细的消息信息
     */
    default void send(String event, MsgEvent msgEvent) {
        send(event, msgEvent.toString());
    }

    /**
     * 向所有客户端发送消息。
     *
     * @param event   触发的事件名称，例如："step_update"
     * @param msgStep 要发送的消息步骤对象，包含执行步骤的信息
     */
    default void send(String event, MsgStep msgStep) {
        send(event, msgStep.toString());
    }

    /**
     * 向指定客户端发送消息。
     *
     * @param clientId 目标客户端的ID，例如："client_001"
     * @param event    触发的事件名称，例如："private_message"
     * @param msg      要发送的消息内容，例如："This is a private message."
     */
    void sendClient(String clientId, String event, String msg);

    /**
     * 获取所有在线用户列表。
     *
     * @param type 用户类型，例如："admin", "user"
     * @return 在线用户列表
     */
    List<SocketUser> getOnlineSession(String type);

    /**
     * 获取指定用户类型的在线用户列表。
     *
     * @param aDefault 用户类型，例如："admin"
     * @param roomId   房间ID，例如："room_001"
     * @param target   目标用户标识，例如："target_user_001"
     * @return 指定条件下的Socket会话实例
     */
    SocketSession getOnlineSession(String aDefault, String roomId, String target);

    /**
     * 获取指定房间内的所有在线用户会话。
     *
     * @param aDefault 用户类型，例如："admin"
     * @param roomId   房间ID，例如："room_001"
     * @return 房间内所有在线用户的会话列表
     */
    List<SocketSession> getOnlineSession(String aDefault, String roomId);

    /**
     * 创建Socket服务器信息列表。
     *
     * @return Socket服务器信息列表
     */
    List<SocketInfo> createSocketInfo();

    /**
     * 获取指定ID的Socket服务器实例。
     *
     * @param serverId 服务器ID，例如："server_001"
     * @return 对应的Socket服务器实例，如果未找到则返回null
     */
    DelegateSocketIOServer getSocketServer(String serverId);

    /**
     * Socket服务器信息类，用于封装服务器相关组件。
     */
    @Data
    @AllArgsConstructor
    class SocketInfo {
        /**
         * SocketIO服务器实例。
         */
        private DelegateSocketIOServer server;

        /**
         * 会话解析器。
         */
        private SocketSessionResolver resolver;

        /**
         * 会话模板。
         */
        private SocketSessionTemplate template;

        /**
         * 配置信息。
         */
        private WrapperConfiguration wrapperConfiguration;
    }
}

