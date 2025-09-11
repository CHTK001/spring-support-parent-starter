package com.chua.webrtc.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WebRTC配置属性
 *
 * @author CH
 * @since 2023-09-20
 */
@Data
@ConfigurationProperties(prefix = "plugin.webrtc")
public class WebRtcProperties {

    /**
     * 是否启用WebRTC功能
     * 默认值: true
     * 示例: plugin.webrtc.enabled=true
     */
    private boolean enabled = true;

    /**
     * 服务器ID
     * 默认值: webrtc
     * 示例: plugin.webrtc.server-id=webrtc
     */
    private String serverId = "webrtc";

    /**
     * STUN服务器配置信息
     * 用于NAT穿透的STUN服务器配置
     */
    private StunServer stunServer = new StunServer();

    /**
     * TURN服务器配置信息
     * 用于NAT穿透的TURN服务器配置（当STUN无法穿透时使用）
     */
    private TurnServer turnServer = new TurnServer();

    /**
     * 房间相关配置
     * 包括房间最大用户数、超时时间等设置
     */
    private Room room = new Room();


    /**
     * STUN服务器配置类
     *
     * @author CH
     * @since 2023-09-20
     */
    @Data
    public static class StunServer {
        /**
         * STUN服务器地址
         * 默认值: stun:stun.l.google.com:19302
         * 示例: plugin.webrtc.stun-server.url=stun:stun.l.google.com:19302
         */
        private String url = "stun:stun.l.google.com:19302";
    }

    /**
     * TURN服务器配置类
     *
     * @author CH
     * @since 2023-09-20
     */
    @Data
    public static class TurnServer {
        /**
         * TURN服务器地址
         * 示例: plugin.webrtc.turn-server.url=turn:turn.example.com:3478
         */
        private String url;

        /**
         * TURN服务器用户名
         * 示例: plugin.webrtc.turn-server.username=myuser
         */
        private String username;

        /**
         * TURN服务器凭证（密码）
         * 示例: plugin.webrtc.turn-server.credential=mypassword
         */
        private String credential;
    }

    /**
     * 房间配置类
     *
     * @author CH
     * @since 2023-09-20
     */
    @Data
    public static class Room {
        /**
         * 房间最大用户数
         * 默认值: 10
         * 示例: plugin.webrtc.room.max-users=20
         */
        private int maxUsers = 10;

        /**
         * 房间超时时间（分钟）
         * 默认值: 60
         * 示例: plugin.webrtc.room.timeout-minutes=120
         */
        private long timeoutMinutes = 60;
    }
}