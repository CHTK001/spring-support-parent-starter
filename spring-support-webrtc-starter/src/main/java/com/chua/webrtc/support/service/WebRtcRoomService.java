package com.chua.webrtc.support.service;

import com.chua.webrtc.support.entity.WebRtcRoom;
import com.chua.webrtc.support.entity.WebRtcUser;
import com.chua.webrtc.support.mapper.WebRtcRoomMapper;
import com.chua.webrtc.support.mapper.WebRtcUserMapper;
import com.chua.webrtc.support.model.Room;
import com.chua.webrtc.support.model.User;
import com.chua.webrtc.support.properties.WebRtcProperties;
import com.chua.webrtc.support.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

/**
 * WebRTC房间管理服务
 *
 * @author CH
 * @since 4.1.0
 */
@Service
public class WebRtcRoomService {

    private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> roomNumberToId = new ConcurrentHashMap<>(); // 房间号到房间ID的映射
    private final WebRtcProperties properties;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final SnowflakeIdGenerator snowflakeIdGenerator = SnowflakeIdGenerator.getInstance();

    @Autowired(required = false)
    private WebRtcRoomMapper webRtcRoomMapper;

    @Autowired(required = false)
    private WebRtcUserMapper webRtcUserMapper;

    public WebRtcRoomService(WebRtcProperties properties) {
        this.properties = properties;
        // 启动定时清理任务
        scheduler.scheduleAtFixedRate(this::cleanupExpiredRooms, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * 创建房间
     */
    @Transactional
    public Room createRoom(String creatorId) {
        String roomId = generateRoomId();
        Long roomNumber = snowflakeIdGenerator.generateRoomNumber();
        Room room = new Room(roomId, roomNumber, creatorId);
        rooms.put(roomId, room);
        roomNumberToId.put(roomNumber, roomId);

        // 持久化到数据库
        if (webRtcRoomMapper != null) {
            WebRtcRoom webRtcRoom = new WebRtcRoom();
            webRtcRoom.setWebrtcRoomId(roomId);
            webRtcRoom.setWebrtcRoomNumber(roomNumber);
            webRtcRoom.setWebrtcRoomCreatorId(creatorId);
            webRtcRoom.setWebrtcRoomStatus("ACTIVE");
            webRtcRoom.setWebrtcRoomCurrentUsers(0);
            webRtcRoom.setWebrtcRoomMaxUsers(properties.getRoom().getMaxUsers());
            webRtcRoom.setWebrtcRoomCreateTime(LocalDateTime.now());
            webRtcRoom.setWebrtcRoomLastActiveTime(LocalDateTime.now());
            webRtcRoom.setWebrtcRoomDeleted(false);
            webRtcRoomMapper.insert(webRtcRoom);
        }

        return room;
    }

    /**
     * 获取房间
     */
    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /**
     * 通过房间号获取房间
     */
    public Room getRoomByNumber(Long roomNumber) {
        String roomId = roomNumberToId.get(roomNumber);
        return roomId != null ? rooms.get(roomId) : null;
    }

    /**
     * 获取所有房间
     */
    public List<Room> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }

    /**
     * 用户加入房间
     */
    public boolean joinRoom(String roomId, User user) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return false;
        }

        // 检查房间用户数量限制
        if (room.getUserCount() >= properties.getRoom().getMaxUsers()) {
            return false;
        }

        return room.addUser(user);
    }

    /**
     * 用户离开房间
     */
    @Transactional
    public User leaveRoom(String roomId, String userId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            return null;
        }

        User user = room.removeUser(userId);

        // 更新数据库中的用户状态
        if (webRtcUserMapper != null && user != null) {
            webRtcUserMapper.leaveRoom(userId, LocalDateTime.now());
        }

        // 更新房间当前用户数
        if (webRtcRoomMapper != null) {
            webRtcRoomMapper.updateCurrentUsers(roomId, room.getUserCount());
        }

        // 如果房间为空，标记为非活跃并自动关闭
        if (room.isEmpty()) {
            room.setStatus(Room.RoomStatus.INACTIVE);
            autoCloseEmptyRoom(roomId);
        }

        return user;
    }

    /**
     * 删除房间
     */
    @Transactional
    public boolean removeRoom(String roomId) {
        Room room = rooms.remove(roomId);
        if (room != null && room.getRoomNumber() != null) {
            roomNumberToId.remove(room.getRoomNumber());

            // 从数据库中删除房间
            if (webRtcRoomMapper != null) {
                webRtcRoomMapper.closeRoom(roomId, LocalDateTime.now());
                // 清空房间内所有用户的房间信息
                if (webRtcUserMapper != null) {
                    webRtcUserMapper.clearRoomUsers(roomId, LocalDateTime.now());
                }
            }
        }
        return room != null;
    }

    /**
     * 创建人关闭房间
     */
    @Transactional
    public boolean closeRoomByCreator(String roomId, String creatorId) {
        Room room = rooms.get(roomId);
        if (room == null || !creatorId.equals(room.getCreatorId())) {
            return false;
        }

        // 踢出所有用户
        room.clearUsers();
        room.setStatus(Room.RoomStatus.CLOSED);

        // 更新数据库
        if (webRtcRoomMapper != null) {
            webRtcRoomMapper.closeRoom(roomId, LocalDateTime.now());
            if (webRtcUserMapper != null) {
                webRtcUserMapper.clearRoomUsers(roomId, LocalDateTime.now());
            }
        }

        // 从内存中移除房间
        rooms.remove(roomId);
        if (room.getRoomNumber() != null) {
            roomNumberToId.remove(room.getRoomNumber());
        }

        return true;
    }

    /**
     * 自动关闭空房间
     */
    @Transactional
    public void autoCloseEmptyRoom(String roomId) {
        Room room = rooms.get(roomId);
        if (room != null && room.isEmpty()) {
            // 延迟5分钟后关闭空房间
            scheduler.schedule(() -> {
                Room currentRoom = rooms.get(roomId);
                if (currentRoom != null && currentRoom.isEmpty()) {
                    removeRoom(roomId);
                }
            }, 5, TimeUnit.MINUTES);
        }
    }

    /**
     * 清理过期房间
     */
    private void cleanupExpiredRooms() {
        LocalDateTime expireTime = LocalDateTime.now().minusMinutes(properties.getRoom().getTimeoutMinutes());

        rooms.entrySet().removeIf(entry -> {
            Room room = entry.getValue();
            boolean shouldRemove = room.isEmpty() && room.getLastActiveTime().isBefore(expireTime);
            if (shouldRemove && room.getRoomNumber() != null) {
                roomNumberToId.remove(room.getRoomNumber());
            }
            return shouldRemove;
        });
    }

    /**
     * 生成房间ID
     */
    private String generateRoomId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 获取房间数量
     */
    public int getRoomCount() {
        return rooms.size();
    }

    /**
     * 检查房间是否存在
     */
    public boolean roomExists(String roomId) {
        return rooms.containsKey(roomId);
    }
}