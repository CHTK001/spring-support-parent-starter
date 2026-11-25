package com.chua.starter.socket.websocket.room;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * P2P 房间信令配置（ZestSend Room 信令引擎）。
 * <p>
 * 配置前缀为 {@code room}。注意：本模块的自动配置类未通过 {@code @EnableConfigurationProperties} 注册本类，
 * 同端口信令链路的 {@code roomService(RoomProperties, WebSocketTemplate)} 需要宿主自行提供该 Bean
 * （例如在宿主配置类上加 {@code @EnableConfigurationProperties(RoomProperties.class)}）；
 * 独立端口的 {@code RoomServer} 节点在构造时自行 {@code new RoomProperties()}，不经 Spring 绑定。
 * </p>
 * <p>完整 YAML 配置示例：</p>
 * <pre>{@code
 * room:
 *   enable: true                          # 文档性开关，当前不控制任何 Bean 创建
 *   endpoint: /api/rooms/{roomId}/ws      # 文档性端点描述，实际端点由 plugin.websocket.endpoints 决定
 *   max-peers: 2                          # 每房间最大参与端点数（P2P = 2）
 *   timeouts:
 *     lease-expires-ms: 30000             # 端点心跳续期时间，超时判定掉线
 *     remove-empty-room-ms: 240000        # 无活跃端点房间的回收时间
 *   stun-server: "stun:stun.example.com:3478"   # STUN 服务器（预留字段）
 *   turn-server: "turn:turn.example.com:3478"   # TURN 服务器 URI（预留字段）
 *   turn-user: "your-turn-user"           # TURN 用户名（预留字段）
 *   turn-password: "change-me"            # TURN 密码（预留字段，示例用占位符）
 * }</pre>
 *
 * @author CH
 * @since 2026-11-03
 */
@Data
@ConfigurationProperties(prefix = "room")
public class RoomProperties {

    /**
     * 是否启用房间信令模块
     * <p>
     * 默认 {@code false}；当前为文档性开关，源码未消费，不控制任何 Bean 创建。
     * </p>
     * <pre>{@code
     * room:
     *   enable: true
     * }</pre>
     */
    private boolean enable = false;

    /**
     * 房间信令 WebSocket 端点
     * <p>
     * 默认 {@code /api/rooms/{roomId}/ws}；文档性描述，当前源码未消费，
     * 实际端点由 {@code plugin.websocket.endpoints} 决定。
     * </p>
     * <pre>{@code
     * room:
     *   endpoint: /api/rooms/{roomId}/ws
     * }</pre>
     */
    private String endpoint = "/api/rooms/{roomId}/ws";

    /**
     * 每房间最大参与端点数（P2P = 2）
     * <p>
     * 默认 {@code 2}；当前源码仅在 {@code RoomServer} 启动日志中打印，房间引擎未据此强制拒绝加入。
     * {@code <=0} 时本模块不做拦截，原样保留。
     * </p>
     * <pre>{@code
     * room:
     *   max-peers: 2
     * }</pre>
     */
    private int maxPeers = 2;

    /**
     * 房间超时配置
     * <p>
     * 默认实例化 {@link RoomTimeouts}（{@code lease-expires-ms=30000}、{@code remove-empty-room-ms=240000}）；
     * 由房间引擎用于心跳续期与空房间回收。
     * </p>
     * <pre>{@code
     * room:
     *   timeouts:
     *     lease-expires-ms: 30000
     *     remove-empty-room-ms: 240000
     * }</pre>
     */
    private RoomTimeouts timeouts = new RoomTimeouts();

    /**
     * STUN 服务器（内网发现）
     * <p>
     * 默认空串；当前模块源码未消费，为预留字段。
     * </p>
     * <pre>{@code
     * room:
     *   stun-server: "stun:stun.example.com:3478"
     * }</pre>
     */
    private String stunServer = "";

    /**
     * TURN 服务器 URI（中继失败时备用）
     * <p>
     * 默认空串；当前模块源码未消费，为预留字段。
     * </p>
     * <pre>{@code
     * room:
     *   turn-server: "turn:turn.example.com:3478"
     * }</pre>
     */
    private String turnServer = "";

    /**
     * TURN 用户
     * <p>
     * 默认空串；当前模块源码未消费，为预留字段。
     * </p>
     * <pre>{@code
     * room:
     *   turn-user: "your-turn-user"
     * }</pre>
     */
    private String turnUser = "";

    /**
     * TURN 密码
     * <p>
     * 默认空串；当前模块源码未消费，为预留字段。示例一律使用占位符，勿写入真实凭据。
     * </p>
     * <pre>{@code
     * room:
     *   turn-password: "change-me"
     * }</pre>
     */
    private String turnPassword = "";

    /**
     * 房间超时配置。
     * <p>完整 YAML 配置示例：</p>
     * <pre>{@code
     * room:
     *   timeouts:
     *     lease-expires-ms: 30000       # 端点心跳续期时间，超时判定掉线
     *     remove-empty-room-ms: 240000  # 无活跃端点房间的回收时间
     * }</pre>
     */
    public static class RoomTimeouts {

        /**
         * 端点续期时间（ms） - 心跳未到达则端点断线
         * <p>
         * 默认 {@code 30000}；房间引擎在加入/心跳时按 {@code now + lease-expires-ms} 续期租约。
         * 本模块不校验取值，{@code <=0} 时新租约会立即到期（等于关闭续期，慎用）。
         * </p>
         * <pre>{@code
         * room:
         *   timeouts:
         *     lease-expires-ms: 30000
         * }</pre>
         */
        private long leaseExpiresMs = 30_000;

        /**
         * 无参与者房间回收时间（ms）
         * <p>
         * 默认 {@code 240000}；守护线程清理时，对「无活跃端点且距最后活跃时间超过该值」的房间回收。
         * 本模块不校验取值，{@code <=0} 时空房间在下一次清理轮询即满足回收条件。
         * </p>
         * <pre>{@code
         * room:
         *   timeouts:
         *     remove-empty-room-ms: 240000
         * }</pre>
         */
        private long removeEmptyRoomMs = 240_000;

        /**
         * 获取端点续期时间。
         *
         * @return 端点续期时间（ms）
         */
        public long getLeaseExpiresMs() {
            return leaseExpiresMs;
        }

        /**
         * 设置端点续期时间。
         *
         * @param leaseExpiresMs 端点续期时间（ms）
         */
        public void setLeaseExpiresMs(long leaseExpiresMs) {
            this.leaseExpiresMs = leaseExpiresMs;
        }

        /**
         * 获取无参与者房间回收时间。
         *
         * @return 无参与者房间回收时间（ms）
         */
        public long getRemoveEmptyRoomMs() {
            return removeEmptyRoomMs;
        }

        /**
         * 设置无参与者房间回收时间。
         *
         * @param removeEmptyRoomMs 无参与者房间回收时间（ms）
         */
        public void setRemoveEmptyRoomMs(long removeEmptyRoomMs) {
            this.removeEmptyRoomMs = removeEmptyRoomMs;
        }
    }
}
