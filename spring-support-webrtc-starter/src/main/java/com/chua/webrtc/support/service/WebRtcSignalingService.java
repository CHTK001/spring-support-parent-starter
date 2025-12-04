package com.chua.webrtc.support.service;

import com.chua.socketio.support.constant.SocketTopics;
import com.chua.webrtc.support.dto.*;
import com.chua.webrtc.support.model.Room;
import com.chua.webrtc.support.model.User;
import com.corundumstudio.socketio.SocketIOClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WebRTC信令服务
 *
 * @author CH
 * @since 4.1.0
 */
@Service
@RequiredArgsConstructor
public class WebRtcSignalingService {

    private final WebRtcRoomService roomService;

    /**
     * 处理用户加入房间
     *
     * @param client  客户端连接
     * @param request 加入房间请求
     */
    public void handleJoinRoom(SocketIOClient client, JoinRoomRequest request) {
        String roomId = request.getRoomId();
        String userId = request.getUserId();
        String username = request.getUsername();

        if (roomId == null || userId == null) {
            client.sendEvent("error", "Missing roomId or userId");
            return;
        }

        // 检查房间是否存在，不存在则创建
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            room = roomService.createRoom(userId);
            room.setRoomId(roomId);
        }

        // 检查用户是否已经在房间中
        User existingUser = room.getUser(userId);
        if (existingUser != null) {
            // 如果用户已存在，关闭旧的连接并更新SocketIOClient
            SocketIOClient oldClient = existingUser.getClient();
            if (oldClient != null && !oldClient.equals(client)) {
                // 通知旧客户端连接被替换
                oldClient.sendEvent("connectionReplaced", "Your connection has been replaced by a new one");
                oldClient.disconnect();
            }

            // 更新现有用户的SocketIOClient
            existingUser.setSocketIOClient(client);

            // 通知用户重新连接成功
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("roomId", roomId);
            response.put("userId", userId);
            response.put("reconnected", true);
            client.sendEvent("joined", response);

            // 发送房间内现有用户列表
            sendRoomUsers(client, room);
            return;
        }

        // 创建新用户并加入房间
        User user = new User(userId, username != null ? username : userId, client);
        boolean joined = roomService.joinRoom(roomId, user);

        if (joined) {
            // 通知用户加入成功
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("roomId", roomId);
            response.put("userId", userId);
            client.sendEvent("joined", response);

            // 通知房间内其他用户
            notifyOthersInRoom(room, userId, "userJoined", user.toUserInfo());

            // 发送房间内现有用户列表
            sendRoomUsers(client, room);
        } else {
            client.sendEvent("joinFailed", "Failed to join room");
        }
    }

    /**
     * 处理用户离开房间
     *
     * @param client  客户端连接
     * @param request 离开房间请求
     */
    public void handleLeaveRoom(SocketIOClient client, LeaveRoomRequest request) {
        String roomId = request.getRoomId();
        String userId = request.getUserId();

        if (roomId == null || userId == null) {
            return;
        }

        Room room = roomService.getRoom(roomId);
        if (room != null) {
            User user = roomService.leaveRoom(roomId, userId);
            if (user != null) {
                // 通知房间内其他用户
                notifyOthersInRoom(room, userId, "userLeft", user.toUserInfo());
            }
        }
    }

    /**
     * 处理WebRTC Offer
     *
     * @param client  客户端连接
     * @param request Offer请求
     */
    public void handleOffer(SocketIOClient client, OfferRequest request) {
        String roomId = request.getRoomId();
        String targetUserId = request.getToUserId();
        Object offer = request.getOffer();

        Room room = roomService.getRoom(roomId);
        if (room != null) {
            User targetUser = room.getUser(targetUserId);
            if (targetUser != null) {
                Map<String, Object> offerData = new HashMap<>();
                offerData.put("offer", offer);
                offerData.put("fromUserId", getUserIdFromClient(client, room));
                targetUser.getClient().sendEvent("offer", offerData);
            }
        }
    }

    /**
     * 处理WebRTC Answer
     *
     * @param client  客户端连接
     * @param request Answer请求
     */
    public void handleAnswer(SocketIOClient client, AnswerRequest request) {
        String roomId = request.getRoomId();
        String targetUserId = request.getToUserId();
        Object answer = request.getAnswer();

        Room room = roomService.getRoom(roomId);
        if (room != null) {
            User targetUser = room.getUser(targetUserId);
            if (targetUser != null) {
                Map<String, Object> answerData = new HashMap<>();
                answerData.put("answer", answer);
                answerData.put("fromUserId", getUserIdFromClient(client, room));
                targetUser.getClient().sendEvent("answer", answerData);
            }
        }
    }

    /**
     * 处理ICE Candidate
     *
     * @param client  客户端连接
     * @param request ICE候选请求
     */
    public void handleIceCandidate(SocketIOClient client, IceCandidateRequest request) {
        String roomId = request.getRoomId();
        String targetUserId = request.getToUserId();
        Object candidate = request.getCandidate();

        Room room = roomService.getRoom(roomId);
        if (room != null) {
            User targetUser = room.getUser(targetUserId);
            if (targetUser != null) {
                Map<String, Object> candidateData = new HashMap<>();
                candidateData.put("candidate", candidate);
                candidateData.put("fromUserId", getUserIdFromClient(client, room));
                targetUser.getClient().sendEvent("iceCandidate", candidateData);
            }
        }
    }

    /**
     * 处理媒体状态变化
     *
     * @param client  客户端连接
     * @param request 媒体状态变化请求
     */
    public void handleMediaStateChange(SocketIOClient client, MediaStateChangeRequest request) {
        String roomId = request.getRoomId();
        String userId = request.getUserId();
        Boolean audioEnabled = request.getAudioEnabled();
        Boolean videoEnabled = request.getVideoEnabled();
        Boolean screenSharing = request.getScreenSharing();

        Room room = roomService.getRoom(roomId);
        if (room != null) {
            User user = room.getUser(userId);
            if (user != null) {
                if (audioEnabled != null) {
                    user.setAudioEnabled(audioEnabled);
                }
                if (videoEnabled != null) {
                    user.setVideoEnabled(videoEnabled);
                }
                if (screenSharing != null) {
                    user.setScreenSharing(screenSharing);
                }
                // 通知房间内其他用户
                notifyOthersInRoom(room, userId, "mediaStateChanged", user.toUserInfo());
            }
        }
    }

    /**
     * 获取房间信息
     *
     * @param client  客户端连接
     * @param request 房间信息请求
     */
    public void handleGetRoomInfo(SocketIOClient client, RoomInfoRequest request) {
        String roomId = request.getRoomId();

        Room room = roomService.getRoom(roomId);
        if (room != null) {
            sendRoomUsers(client, room);
        } else {
            client.sendEvent("error", "Room not found");
        }
    }

    /**
     * 发送房间用户列表
     *
     * @param client 客户端连接
     * @param room   房间对象
     */
    private void sendRoomUsers(SocketIOClient client, Room room) {
        List<User.UserInfo> users = room.getAllUsers().stream()
                .map(User::toUserInfo)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("roomId", room.getRoomId());
        response.put("users", users);
        client.sendEvent("roomUsers", response);
    }

    /**
     * 通知房间内其他用户
     *
     * @param room          房间对象
     * @param excludeUserId 排除的用户ID
     * @param event         事件名称
     * @param data          事件数据
     */
    private void notifyOthersInRoom(Room room, String excludeUserId, String event, Object data) {
        room.getAllUsers().stream()
                .filter(user -> !user.getUserId().equals(excludeUserId))
                .forEach(user -> user.getClient().sendEvent(event, data));
    }

    /**
     * 从客户端获取用户ID
     *
     * @param client 客户端连接
     * @param room   房间对象
     * @return 用户ID，如果未找到则返回null
     */
    private String getUserIdFromClient(SocketIOClient client, Room room) {
        return room.getAllUsers().stream()
                .filter(user -> user.getClient().equals(client))
                .map(User::getUserId)
                .findFirst()
                .orElse(null);
    }
}