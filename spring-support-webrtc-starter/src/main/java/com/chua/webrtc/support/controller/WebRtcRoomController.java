package com.chua.webrtc.support.controller;

import com.chua.webrtc.support.model.Room;
import com.chua.webrtc.support.service.WebRtcRoomCleanupService;
import com.chua.webrtc.support.service.WebRtcRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebRTC房间管理控制器
 *
 * @author CH
 * @since 4.1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/webrtc/room")
@Tag(name = "WebRTC房间管理", description = "WebRTC房间相关API")
@ConditionalOnProperty(prefix = "webrtc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebRtcRoomController {

    @Autowired
    private WebRtcRoomService webRtcRoomService;

    @Autowired(required = false)
    private WebRtcRoomCleanupService webRtcRoomCleanupService;

    /**
     * 创建房间
     */
    @PostMapping("/create")
    @Operation(summary = "创建房间", description = "创建一个新的WebRTC房间")
    public ResponseEntity<Map<String, Object>> createRoom(
            @Parameter(description = "创建人ID", required = true)
            @RequestParam String creatorId,
            @Parameter(description = "房间名称")
            @RequestParam(required = false) String roomName) {

        try {
            Room room = webRtcRoomService.createRoom(creatorId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "房间创建成功");
            result.put("data", Map.of(
                    "roomId", room.getId(),
                    "roomNumber", room.getRoomNumber(),
                    "creatorId", room.getCreatorId(),
                    "status", room.getStatus().name(),
                    "createTime", room.getCreateTime()
            ));

            log.info("用户 {} 创建房间成功: {} (房间号: {})", creatorId, room.getId(), room.getRoomNumber());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("创建房间失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "创建房间失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 关闭房间（仅创建人可操作）
     */
    @PostMapping("/close")
    @Operation(summary = "关闭房间", description = "创建人关闭房间，会踢出所有用户")
    public ResponseEntity<Map<String, Object>> closeRoom(
            @Parameter(description = "房间ID", required = true)
            @RequestParam String roomId,
            @Parameter(description = "创建人ID", required = true)
            @RequestParam String creatorId) {

        try {
            boolean success = webRtcRoomService.closeRoomByCreator(roomId, creatorId);

            Map<String, Object> result = new HashMap<>();
            if (success) {
                result.put("success", true);
                result.put("message", "房间关闭成功");
                log.info("创建人 {} 关闭房间: {}", creatorId, roomId);
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "房间关闭失败：房间不存在或您不是创建人");
                return ResponseEntity.badRequest().body(result);
            }

        } catch (Exception e) {
            log.error("关闭房间失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "关闭房间失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 获取房间信息
     */
    @GetMapping("/info/{roomId}")
    @Operation(summary = "获取房间信息", description = "根据房间ID获取房间详细信息")
    public ResponseEntity<Map<String, Object>> getRoomInfo(
            @Parameter(description = "房间ID", required = true)
            @PathVariable String roomId) {

        try {
            Room room = webRtcRoomService.getRoom(roomId);

            Map<String, Object> result = new HashMap<>();
            if (room != null) {
                result.put("success", true);
                result.put("data", Map.of(
                        "roomId", room.getId(),
                        "roomNumber", room.getRoomNumber(),
                        "creatorId", room.getCreatorId(),
                        "status", room.getStatus().name(),
                        "userCount", room.getUserCount(),
                        "users", room.getUsers(),
                        "createTime", room.getCreateTime(),
                        "lastActiveTime", room.getLastActiveTime()
                ));
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "房间不存在");
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("获取房间信息失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取房间信息失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 获取所有房间列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取房间列表", description = "获取所有活跃房间的列表")
    public ResponseEntity<Map<String, Object>> getRoomList() {

        try {
            List<Room> rooms = webRtcRoomService.getAllRooms();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", rooms.stream().map(room -> Map.of(
                    "roomId", room.getId(),
                    "roomNumber", room.getRoomNumber(),
                    "creatorId", room.getCreatorId(),
                    "status", room.getStatus().name(),
                    "userCount", room.getUserCount(),
                    "createTime", room.getCreateTime(),
                    "lastActiveTime", room.getLastActiveTime()
            )).toList());
            result.put("total", rooms.size());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("获取房间列表失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取房间列表失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 获取房间统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取房间统计", description = "获取房间和用户的统计信息")
    public ResponseEntity<Map<String, Object>> getRoomStatistics() {

        try {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalRooms", webRtcRoomService.getRoomCount());

            // 如果有数据库支持，获取更详细的统计信息
            if (webRtcRoomCleanupService != null) {
                WebRtcRoomCleanupService.RoomStatistics roomStats = webRtcRoomCleanupService.getRoomStatistics();
                stats.put("activeRooms", roomStats.getActiveRooms());
                stats.put("totalUsers", roomStats.getTotalUsers());
            }

            result.put("data", stats);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("获取房间统计失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取房间统计失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 手动清理过期房间（管理员接口）
     */
    @PostMapping("/cleanup")
    @Operation(summary = "清理过期房间", description = "手动触发清理过期和空房间")
    public ResponseEntity<Map<String, Object>> cleanupRooms() {

        try {
            Map<String, Object> result = new HashMap<>();

            if (webRtcRoomCleanupService != null) {
                webRtcRoomCleanupService.cleanupExpiredRooms();
                webRtcRoomCleanupService.cleanupEmptyRooms();
                result.put("success", true);
                result.put("message", "房间清理完成");
                log.info("手动触发房间清理完成");
            } else {
                result.put("success", false);
                result.put("message", "房间清理服务不可用");
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("清理房间失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "清理房间失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
}