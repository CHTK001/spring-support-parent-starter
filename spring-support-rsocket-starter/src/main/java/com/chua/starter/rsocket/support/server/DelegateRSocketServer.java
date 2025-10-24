package com.chua.starter.rsocket.support.server;

import com.chua.rsocket.support.protocol.RSocketProtocol;
import com.chua.rsocket.support.server.RSocketProtocolServer;
import com.chua.common.support.protocol.ProtocolSetting;
import com.chua.common.support.protocol.ServerSetting;
import com.chua.starter.rsocket.support.auth.RSocketAuthFactory;
import com.chua.starter.rsocket.support.properties.RSocketProperties;
import com.chua.starter.rsocket.support.resolver.RSocketSessionResolver;
import com.chua.starter.rsocket.support.session.RSocketSession;
import com.chua.starter.rsocket.support.session.RSocketSessionTemplate;
import io.rsocket.RSocket;
import lombok.extern.slf4j.Slf4j;

/**
 * RSocket服务器委托类
 * <p>
 * 封装底层RSocketProtocolServer，提供Spring Boot集成支持
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
@Slf4j
public class DelegateRSocketServer {

    /**
     * 底层RSocket协议服务器
     */
    private RSocketProtocolServer protocolServer;

    /**
     * RSocket配置属性
     */
    private final RSocketProperties properties;

    /**
     * 会话模板
     */
    private final RSocketSessionTemplate sessionTemplate;

    /**
     * 会话解析器
     */
    private final RSocketSessionResolver sessionResolver;

    /**
     * 认证工厂
     */
    private final RSocketAuthFactory authFactory;

    /**
     * 构造函数
     * 
     * @param properties      RSocket配置属性
     * @param sessionTemplate 会话模板
     * @param sessionResolver 会话解析器
     * @param authFactory     认证工厂
     */
    public DelegateRSocketServer(
            RSocketProperties properties,
            RSocketSessionTemplate sessionTemplate,
            RSocketSessionResolver sessionResolver,
            RSocketAuthFactory authFactory) {
        
        this.properties = properties;
        this.sessionTemplate = sessionTemplate;
        this.sessionResolver = sessionResolver;
        this.authFactory = authFactory;
    }

    /**
     * 启动服务器
     */
    public void start() {
        try {
            // 创建服务器配置
            ServerSetting serverSetting = ServerSetting.builder()
                    .host(properties.getHost())
                    .port(properties.getPort())
                    .bossThreads(properties.getBossCount())
                    .workerThreads(properties.getWorkCount())
                    .maxFrameSize(properties.getMaxFramePayloadLength())
                    .build();

            // 创建并启动协议服务器
            protocolServer = new RSocketProtocolServer(serverSetting);
            protocolServer.start();

            log.info("RSocket服务器启动成功: {}:{}", properties.getHost(), properties.getPort());
        } catch (Exception e) {
            log.error("RSocket服务器启动失败", e);
            throw new RuntimeException("RSocket服务器启动失败", e);
        }
    }

    /**
     * 停止服务器
     */
    public void stop() {
        try {
            if (protocolServer != null) {
                protocolServer.stop();
                log.info("RSocket服务器已停止");
            }
        } catch (Exception e) {
            log.error("RSocket服务器停止失败", e);
        }
    }

    /**
     * 广播消息
     * 
     * @param event 事件名称
     * @param data  消息数据
     */
    public void broadcast(String event, Object data) {
        sessionTemplate.broadcastObject(event, data);
    }

    /**
     * 发送消息到指定会话
     * 
     * @param sessionId 会话ID
     * @param event     事件名称
     * @param data      消息数据
     */
    public void sendToSession(String sessionId, String event, Object data) {
        sessionTemplate.sendObject(sessionId, event, data);
    }

    /**
     * 发送消息到指定用户
     * 
     * @param userId 用户ID
     * @param event  事件名称
     * @param data   消息数据
     */
    public void sendToUser(String userId, String event, Object data) {
        sessionTemplate.sendToUserObject(userId, event, data);
    }

    /**
     * 断开指定会话
     * 
     * @param sessionId 会话ID
     */
    public void disconnectSession(String sessionId) {
        sessionTemplate.disconnect(sessionId);
    }

    /**
     * 获取在线会话数量
     * 
     * @return 在线会话数量
     */
    public int getOnlineCount() {
        return sessionTemplate.getOnlineCount();
    }

    /**
     * 获取底层协议服务器
     * 
     * @return RSocket协议服务器
     */
    public RSocketProtocolServer getProtocolServer() {
        return protocolServer;
    }
}

