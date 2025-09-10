package com.chua.webrtc.support.service;

import com.chua.webrtc.support.entity.WebRtcRoom;
import com.chua.webrtc.support.mapper.WebRtcRoomMapper;
import com.chua.webrtc.support.mapper.WebRtcUserMapper;
import com.chua.webrtc.support.properties.WebRtcProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WebRTC房间清理服务
 * 负责自动关闭空房间和过期房间
 *
 * @author CH
 * @since 4.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean({WebRtcRoomMapper.class, WebRtcUserMapper.class})
public class WebRtcRoomCleanupService {
    final WebRtcRoomMapper webRtcRoomMapper;
    final WebRtcUserMapper webRtcUserMapper;
    final WebRtcProperties properties;
    final WebRtcRoomService webRtcRoomService;

    /**
     * 定时清理过期房间
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    @Transactional
    public void cleanupExpiredRooms() {
        try {
            // 计算过期时间
            LocalDateTime expireTime = LocalDateTime.now().minusMinutes(properties.getRoom().getTimeoutMinutes());

            // 查询过期的房间
            List<WebRtcRoom> expiredRooms = webRtcRoomMapper.selectExpiredRooms(expireTime);

            for (WebRtcRoom room : expiredRooms) {
                // 检查房间是否真的没有用户
                int userCount = webRtcUserMapper.countByRoomId(room.getWebrtcRoomId());
                if (userCount == 0) {
                    // 关闭房间
                    closeRoom(room.getWebrtcRoomId(), "系统自动关闭：房间过期");
                    log.info("自动关闭过期房间: {} (房间号: {})", room.getWebrtcRoomId(), room.getWebrtcRoomNumber());
                }
            }

        } catch (Exception e) {
            log.error("清理过期房间时发生错误", e);
        }
    }

    /**
     * 定时清理空房间
     * 每2分钟执行一次
     */
    @Scheduled(fixedRate = 120000) // 2分钟
    @Transactional
    public void cleanupEmptyRooms() {
        try {
            // 查询所有活跃房间
            List<WebRtcRoom> activeRooms = webRtcRoomMapper.selectActiveRooms();

            for (WebRtcRoom room : activeRooms) {
                // 检查房间用户数量
                int userCount = webRtcUserMapper.countByRoomId(room.getWebrtcRoomId());

                // 更新房间当前用户数
                if (userCount != room.getWebrtcRoomCurrentUsers()) {
                    webRtcRoomMapper.updateCurrentUsers(room.getWebrtcRoomId(), userCount);
                }

                // 如果房间为空且超过5分钟没有活动，则关闭房间
                if (userCount == 0) {
                    LocalDateTime emptyThreshold = LocalDateTime.now().minusMinutes(5);
                    if (room.getWebrtcRoomLastActiveTime().isBefore(emptyThreshold)) {
                        closeRoom(room.getWebrtcRoomId(), "系统自动关闭：房间长时间无人");
                        log.info("自动关闭空房间: {} (房间号: {})", room.getWebrtcRoomId(), room.getWebrtcRoomNumber());
                    }
                }
            }

        } catch (Exception e) {
            log.error("清理空房间时发生错误", e);
        }
    }

    /**
     * 关闭房间
     *
     * @param roomId 房间ID
     * @param reason 关闭原因
     */
    @Transactional
    public void closeRoom(String roomId, String reason) {
        try {
            // 清空房间内所有用户
            webRtcUserMapper.clearRoomUsers(roomId, LocalDateTime.now());

            // 关闭房间
            webRtcRoomMapper.closeRoom(roomId, LocalDateTime.now());

            // 从内存中移除房间
            webRtcRoomService.removeRoom(roomId);

            log.info("房间 {} 已关闭，原因: {}", roomId, reason);

        } catch (Exception e) {
            log.error("关闭房间 {} 时发生错误: {}", roomId, e.getMessage(), e);
        }
    }

    /**
     * 获取房间统计信息
     *
     * @return 房间统计信息
     */
    public RoomStatistics getRoomStatistics() {
        try {
            List<WebRtcRoom> activeRooms = webRtcRoomMapper.selectActiveRooms();
            int totalActiveRooms = activeRooms.size();
            int totalUsers = 0;

            for (WebRtcRoom room : activeRooms) {
                totalUsers += webRtcUserMapper.countByRoomId(room.getWebrtcRoomId());
            }

            return new RoomStatistics(totalActiveRooms, totalUsers);

        } catch (Exception e) {
            log.error("获取房间统计信息时发生错误", e);
            return new RoomStatistics(0, 0);
        }
    }

    /**
     * 房间统计信息
     */
    public static class RoomStatistics {
        private final int activeRooms;
        private final int totalUsers;

        public RoomStatistics(int activeRooms, int totalUsers) {
            this.activeRooms = activeRooms;
            this.totalUsers = totalUsers;
        }

        public int getActiveRooms() {
            return activeRooms;
        }

        public int getTotalUsers() {
            return totalUsers;
        }

        @Override
        public String toString() {
            return String.format("活跃房间: %d, 总用户数: %d", activeRooms, totalUsers);
        }
    }
}