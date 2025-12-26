package com.chua.socket.support.properties;

import com.chua.socket.support.SocketProtocol;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.chua.socket.support.properties.SocketProperties.PRE;

/**
 * Socket 基础配置属性类
 * 提供统一的 Socket 配置，支持多种协议切换
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class SocketProperties {

    public static final String PRE = "plugin.socket";

    /**
     * 是否开启
     */
    private boolean enable;

    /**
     * 房间配置列表
     * <p>
     * 支持配置多个独立的 Socket 服务实例，每个实例可以有不同的端口和路径
     * </p>
     * 示例配置：
     * <pre>
     * plugin:
     *   socket:
     *     enable: true
     *     room:
     *       - client-id: default
     *         port: 9000
     *       - client-id: webrtc
     *         context-path: /webrtc
     *         port: 9001
     *       - client-id: shell
     *         port: 9002
     * </pre>
     */
    private List<Room> room = new ArrayList<>();

    /**
     * 协议类型，默认 socketio
     * 可选值：socketio, rsocket, sse
     */
    private SocketProtocol protocol = SocketProtocol.SOCKETIO;

    /**
     * 多协议配置列表（可选）
     * <p>
     * 支持同时启用多种协议，每种协议可独立配置房间
     * 如果配置了此项，将忽略单一的 protocol 和 room 配置
     * </p>
     * 示例配置：
     * <pre>
     * plugin:
     *   socket:
     *     enable: true
     *     protocols:
     *       - protocol: socketio
     *         room:
     *           - port: 9000
     *       - protocol: rsocket
     *         room:
     *           - port: 9001
     * </pre>
     */
    private List<ProtocolConfig> protocols = new ArrayList<>();

    /**
     * 获取有效的协议配置列表
     * 优先返回 protocols 配置，如果为空则将单一 protocol 配置转换为列表
     *
     * @return 协议配置列表
     */
    public List<ProtocolConfig> getEffectiveProtocols() {
        if (protocols != null && !protocols.isEmpty()) {
            return protocols;
        }
        // 兼容旧配置：转换为单元素列表
        ProtocolConfig config = new ProtocolConfig();
        config.setProtocol(protocol);
        config.setRoom(room);
        return Collections.singletonList(config);
    }

    /**
     * 是否为多协议模式
     *
     * @return 是否配置了多协议
     */
    public boolean isMultiProtocol() {
        return protocols != null && !protocols.isEmpty();
    }

    /**
     * 本地IP
     */
    private String host = "0.0.0.0";


    /**
     * 编解码器类型
     */
    private String codecType = "json";

    /**
     * 是否开启数据加密
     */
    private boolean encryptEnabled = false;

    /**
     * 加密密钥
     */
    private String encryptKey;

    /**
     * 最大帧大小（字节），默认1MB
     */
    private int maxFrameSize = 1048576;

    /**
     * Boss 线程数
     */
    private int bossCount = 1;

    /**
     * 工作线程数
     */
    private int workCount = 100;

    /**
     * 是否允许自定义请求
     */
    private boolean allowCustomRequests = true;

    /**
     * Ping 消息超时时间（毫秒），默认60秒
     */
    private int pingTimeout = 60_000;

    /**
     * Ping 消息间隔（毫秒），默认25秒
     */
    private int pingInterval = 25_000;

    /**
     * 认证工厂类名
     */
    private String authFactory;

    /**
     * 是否使用 Linux 的 Native Epoll
     */
    private boolean useLinuxNativeEpoll;

    /**
     * 协议配置
     */
    @Data
    public static class ProtocolConfig {

        /**
         * 协议类型
         */
        private SocketProtocol protocol = SocketProtocol.SOCKETIO;

        /**
         * 房间配置列表
         */
        private List<Room> room = new ArrayList<>();
    }

    /**
     * 房间配置
     */
    @Data
    public static class Room {

        /**
         * 客户端标识，用于区分不同的服务实例
         */
        private String clientId = "default";

        /**
         * 上下文路径，如 /webrtc、/shell 等
         */
        private String contextPath = "/";

        /**
         * 端口号
         * <ul>
         *   <li>正数：使用指定端口启动独立服务</li>
         *   <li>-1：使用主配置的端口</li>
         *   <li>-2, -3...：使用主配置端口 + 偏移量（如主端口9000，-2表示9001）</li>
         * </ul>
         */
        private int port = -1;

        /**
         * 本地IP（可选，不设置则使用主配置）
         */
        private String host;

        /**
         * 认证工厂类名（可选，不设置则使用主配置）
         */
        private String authFactory;

        /**
         * 是否启用此房间
         */
        private boolean enable = true;

        /**
         * 获取实际端口
         *
         * @param mainPort 主配置端口
         * @return 实际端口
         */
        public int getActualPort(int mainPort) {
            if(port < 0) {
                return mainPort + port;
            }
            return port;
        }

        /**
         * 获取实际主机地址
         *
         * @param mainHost 主配置主机地址
         * @return 实际主机地址
         */
        public String getActualHost(String mainHost) {
            return host != null && !host.isEmpty() ? host : mainHost;
        }
    }
}
