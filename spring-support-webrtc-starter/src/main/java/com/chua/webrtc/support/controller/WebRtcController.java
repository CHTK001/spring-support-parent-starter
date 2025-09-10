package com.chua.webrtc.support.controller;

import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.webrtc.support.model.Room;
import com.chua.webrtc.support.model.User;
import com.chua.webrtc.support.properties.WebRtcProperties;
import com.chua.webrtc.support.service.WebRtcRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WebRTC控制器
 *
 * @author CH
 * @since 4.1.0
 */
@Tag(name = "WebRTC API", description = "WebRTC实时通信相关接口")
@RestController
@RequestMapping("/api/webrtc")
@RequiredArgsConstructor
public class WebRtcController {

    private final WebRtcRoomService roomService;
    private final WebRtcProperties properties;
    final WebRtcProperties webRtcProperties;
    private SocketSessionTemplate socketSessionTemplate;

    /**
     * 获取WebRTC配置信息
     */
    @Operation(summary = "获取WebRTC配置", description = "获取WebRTC相关配置信息，包括STUN/TURN服务器等")
    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", properties.isEnabled());
        config.put("stunServer", properties.getStunServer().getUrl());

        if (properties.getTurnServer().getUrl() != null) {
            Map<String, Object> turnConfig = new HashMap<>();
            turnConfig.put("url", properties.getTurnServer().getUrl());
            turnConfig.put("username", properties.getTurnServer().getUsername());
            turnConfig.put("credential", properties.getTurnServer().getCredential());
            config.put("turnServer", turnConfig);
        }

        config.put("maxUsers", properties.getRoom().getMaxUsers());
        config.put("timeoutMinutes", properties.getRoom().getTimeoutMinutes());

        return config;
    }

    /**
     * 获取所有房间
     */
    @Operation(summary = "获取所有房间", description = "获取系统中所有WebRTC房间的列表")
    @GetMapping("/rooms")
    public List<Map<String, Object>> getAllRooms() {
        return roomService.getAllRooms().stream()
                .map(this::roomToMap)
                .collect(Collectors.toList());
    }

    /**
     * 获取房间信息
     */
    @Operation(summary = "获取房间信息", description = "根据房间ID获取指定房间的详细信息")
    @GetMapping("/rooms/{roomId}")
    public Map<String, Object> getRoom(@Parameter(description = "房间ID") @PathVariable String roomId) {
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            throw new RuntimeException("Room not found");
        }
        return roomToMap(room);
    }

    /**
     * 通过房间号获取房间信息
     */
    @Operation(summary = "通过房间号获取房间信息", description = "根据房间号获取指定房间的详细信息")
    @GetMapping("/rooms/number/{roomNumber}")
    public Map<String, Object> getRoomByNumber(@Parameter(description = "房间号") @PathVariable Long roomNumber) {
        Room room = roomService.getRoomByNumber(roomNumber);
        if (room == null) {
            throw new RuntimeException("Room not found");
        }
        return roomToMap(room);
    }

    /**
     * 创建房间
     */
    @Operation(summary = "创建房间", description = "创建一个新的WebRTC房间")
    @PostMapping("/rooms")
    public Map<String, Object> createRoom(@RequestBody Map<String, String> request) {
        String creatorId = request.get("creatorId");
        if (creatorId == null || creatorId.trim().isEmpty()) {
            throw new RuntimeException("Creator ID is required");
        }

        Room room = roomService.createRoom(creatorId);
        return roomToMap(room);
    }

    /**
     * 获取房间用户列表
     */
    @Operation(summary = "获取房间用户", description = "获取指定房间中的所有用户列表")
    @GetMapping("/rooms/{roomId}/users")
    public List<User.UserInfo> getRoomUsers(@Parameter(description = "房间ID") @PathVariable String roomId) {
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            throw new RuntimeException("Room not found");
        }

        return room.getAllUsers().stream()
                .map(User::toUserInfo)
                .collect(Collectors.toList());
    }

    /**
     * 通过房间号加入房间
     */
    @Operation(summary = "通过房间号加入房间", description = "使用房间号加入指定的WebRTC房间")
    @PostMapping("/rooms/number/{roomNumber}/join")
    public Map<String, Object> joinRoomByNumber(
            @Parameter(description = "房间号") @PathVariable Long roomNumber,
            @RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String username = request.get("username");

        if (userId == null || userId.trim().isEmpty()) {
            throw new RuntimeException("User ID is required");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("Username is required");
        }

        Room room = roomService.getRoomByNumber(roomNumber);
        if (room == null) {
            throw new RuntimeException("Room not found");
        }

        User user = new User(userId, username, null);
        boolean joined = roomService.joinRoom(room.getId(), user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", joined);
        response.put("message", joined ? "Joined room successfully" : "Failed to join room");
        response.put("room", roomToMap(room));
        return response;
    }

    /**
     * 删除房间
     */
    @Operation(summary = "删除房间", description = "删除指定的WebRTC房间")
    @DeleteMapping("/rooms/{roomId}")
    public Map<String, Object> deleteRoom(@Parameter(description = "房间ID") @PathVariable String roomId) {
        boolean removed = roomService.removeRoom(roomId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", removed);
        response.put("message", removed ? "Room deleted successfully" : "Room not found");
        return response;
    }

    /**
     * 获取系统统计信息
     */
    @Operation(summary = "获取系统统计", description = "获取WebRTC系统的统计信息，包括房间数量和用户数量")
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRooms", roomService.getRoomCount());

        int totalUsers = roomService.getAllRooms().stream()
                .mapToInt(Room::getUserCount)
                .sum();
        stats.put("totalUsers", totalUsers);

        return stats;
    }

    /**
     * 将Room对象转换为Map
     */
    private Map<String, Object> roomToMap(Room room) {
        Map<String, Object> roomMap = new HashMap<>();
        roomMap.put("roomId", room.getId());
        roomMap.put("roomNumber", room.getRoomNumber());
        roomMap.put("creatorId", room.getCreatorId());
        roomMap.put("userCount", room.getUserCount());
        roomMap.put("createTime", room.getCreateTime());
        roomMap.put("lastActiveTime", room.getLastActiveTime());
        roomMap.put("status", room.getStatus());
        roomMap.put("isEmpty", room.isEmpty());
        return roomMap;
    }
}