package com.chua.webrtc.support.handler;

import com.chua.socketio.support.server.DelegateSocketIOServer;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.webrtc.support.dto.*;
import com.chua.webrtc.support.properties.WebRtcProperties;
import com.chua.webrtc.support.service.WebRtcSignalingService;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * WebRTC事件处理器
 *
 * @author CH
 * @since 4.1.0
 */
@Component
@Slf4j
public class WebRtcEventHandler {

    private final WebRtcSignalingService signalingService;

    @Autowired(required = false)
    private SocketSessionTemplate socketSessionTemplate;

    @Autowired
    private WebRtcProperties webRtcProperties;

    public WebRtcEventHandler(WebRtcSignalingService signalingService) {
        this.signalingService = signalingService;
    }

    @PostConstruct
    public void init() {
        log.info("WebRTC事件处理器初始化");
        if (socketSessionTemplate != null) {
            log.info("WebRTC事件处理器初始化成功");
            registerEventHandlers();
            return;
        }
        log.info("WebRTC事件处理器初始化失败, 原因: socket.io服务器未启动");
    }

    /**
     * 注册事件处理器
     */
    private void registerEventHandlers() {
        DelegateSocketIOServer socketIOServer = socketSessionTemplate.getSocketServer(webRtcProperties.getServerId());
        if (null == socketIOServer) {
            log.info("WebRTC事件处理器初始化失败, 原因: socket.io服务器未启动");
            return;
        }
        // 连接事件
        socketIOServer.addConnectListener(onConnect());

        // 断开连接事件
        socketIOServer.addDisconnectListener(onDisconnect());

        // WebRTC相关事件
        socketIOServer.addEventListener("joinRoom", JoinRoomRequest.class, (client, data, ackSender) -> {
            signalingService.handleJoinRoom(client, data);
        });

        socketIOServer.addEventListener("leaveRoom", LeaveRoomRequest.class, (client, data, ackSender) -> {
            signalingService.handleLeaveRoom(client, data);
        });

        socketIOServer.addEventListener("offer", OfferRequest.class, (client, data, ackSender) -> {
            signalingService.handleOffer(client, data);
        });

        socketIOServer.addEventListener("answer", AnswerRequest.class, (client, data, ackSender) -> {
            signalingService.handleAnswer(client, data);
        });

        socketIOServer.addEventListener("iceCandidate", IceCandidateRequest.class, (client, data, ackSender) -> {
            signalingService.handleIceCandidate(client, data);
        });

        socketIOServer.addEventListener("mediaStateChange", MediaStateChangeRequest.class, (client, data, ackSender) -> {
            signalingService.handleMediaStateChange(client, data);
        });

        socketIOServer.addEventListener("getRoomInfo", RoomInfoRequest.class, (client, data, ackSender) -> {
            signalingService.handleGetRoomInfo(client, data);
        });
    }

    /**
     * 连接事件处理
     */
    private ConnectListener onConnect() {
        return client -> {
            System.out.println("Client connected: " + client.getSessionId());
        };
    }

    /**
     * 断开连接事件处理
     */
    private DisconnectListener onDisconnect() {
        return client -> {
            System.out.println("Client disconnected: " + client.getSessionId());
            // 这里可以添加清理逻辑，比如从所有房间中移除该用户
        };
    }
}