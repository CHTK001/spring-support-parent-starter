package com.chua.webrtc.support.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * WebRTC房间模型
 *
 * @author CH
 * @since 4.1.0
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Room {

    @EqualsAndHashCode.Include
    private String id;
    private Long roomNumber; // 房间号，纯数字唯一标识
    private String creatorId;
    private ConcurrentMap<String, User> users;
    private LocalDateTime createTime;
    private LocalDateTime lastActiveTime;
    private RoomStatus status;

    public Room(String roomId, String creatorId) {
        this.id = roomId;
        this.creatorId = creatorId;
        this.users = new ConcurrentHashMap<>();
        this.createTime = LocalDateTime.now();
        this.lastActiveTime = LocalDateTime.now();
        this.status = RoomStatus.ACTIVE;
    }

    public Room(String roomId, Long roomNumber, String creatorId) {
        this.id = roomId;
        this.roomNumber = roomNumber;
        this.creatorId = creatorId;
        this.users = new ConcurrentHashMap<>();
        this.createTime = LocalDateTime.now();
        this.lastActiveTime = LocalDateTime.now();
        this.status = RoomStatus.ACTIVE;
    }

    /**
     * 添加用户到房间
     */
    public boolean addUser(User user) {
        if (users.containsKey(user.getUserId())) {
            return false;
        }
        users.put(user.getUserId(), user);
        updateLastActiveTime();
        return true;
    }

    /**
     * 从房间移除用户
     */
    public User removeUser(String userId) {
        User removedUser = users.remove(userId);
        updateLastActiveTime();
        return removedUser;
    }

    /**
     * 获取房间中的用户
     */
    public User getUser(String userId) {
        return users.get(userId);
    }

    /**
     * 获取房间中所有用户
     */
    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    /**
     * 获取房间用户数量
     */
    public int getUserCount() {
        return users.size();
    }

    /**
     * 检查房间是否为空
     */
    public boolean isEmpty() {
        return users.isEmpty();
    }

    /**
     * 更新最后活动时间
     */
    public void updateLastActiveTime() {
        this.lastActiveTime = LocalDateTime.now();
    }

    /**
     * 获取房间ID
     */
    public String getRoomId() {
        return id;
    }

    /**
     * 设置房间ID
     */
    public void setRoomId(String roomId) {
        this.id = roomId;
    }

    public void clearUsers() {
        users.clear();
    }


    /**
     * 房间状态枚举
     */
    public enum RoomStatus {
        ACTIVE,    // 活跃
        INACTIVE,  // 非活跃
        CLOSED     // 已关闭
    }
}