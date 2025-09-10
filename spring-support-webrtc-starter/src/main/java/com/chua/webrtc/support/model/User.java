package com.chua.webrtc.support.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.corundumstudio.socketio.SocketIOClient;

import java.time.LocalDateTime;

/**
 * WebRTC用户模型
 *
 * @author CH
 * @since 4.1.0
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

    @EqualsAndHashCode.Include
    private String id;
    private String username;
    private SocketIOClient client;
    private LocalDateTime joinTime;
    private UserStatus status;
    private boolean audioEnabled;
    private boolean videoEnabled;
    private boolean screenSharing;

    public User(String userId, String username, SocketIOClient client) {
        this.id = userId;
        this.username = username;
        this.client = client;
        this.joinTime = LocalDateTime.now();
        this.status = UserStatus.ONLINE;
        this.audioEnabled = true;
        this.videoEnabled = true;
        this.screenSharing = false;
    }

    /**
     * 转换为传输对象
     */
    public UserInfo toUserInfo() {
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(this.id);
        userInfo.setUsername(this.username);
        userInfo.setJoinTime(this.joinTime);
        userInfo.setStatus(this.status);
        userInfo.setAudioEnabled(this.audioEnabled);
        userInfo.setVideoEnabled(this.videoEnabled);
        userInfo.setScreenSharing(this.screenSharing);
        return userInfo;
    }

    /**
     * 获取用户ID
     */
    public String getUserId() {
        return id;
    }


    /**
     * 用户状态枚举
     */
    public enum UserStatus {
        ONLINE,    // 在线
        OFFLINE,   // 离线
        BUSY       // 忙碌
    }

    /**
     * 用户信息传输对象
     *
     * @author CH
     * @since 4.1.0
     */
    @Data
    public static class UserInfo {
        private String userId;
        private String username;
        private LocalDateTime joinTime;
        private UserStatus status;
        private boolean audioEnabled;
        private boolean videoEnabled;
        private boolean screenSharing;
    }
}