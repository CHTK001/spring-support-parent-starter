package com.chua.socketio.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

import static com.chua.socketio.support.properties.SocketIoProperties.PRE;

/**
 * Socket.IO 配置属性类
 * 用于配置Socket.IO服务器的各项参数
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class SocketIoProperties {


    public static final String PRE = "plugin.socket";

    /**
     * 编解码器类型
     */
    private String codecType = "sm2";
    
    /**
     * 是否开启数据加密（默认开启）
     * 开启后，所有下发的数据将使用配置的编解码器进行加密
     */
    private boolean encryptEnabled = true;
    
    /**
     * 加密密钥（可选）
     * 如果不配置，将使用默认密钥
     */
    private String encryptKey;
    
    /**
     * 是否开启
     */
    private boolean enable;
    /**
     * 本地IP
     */
    private String host = "0.0.0.0";
    /**
     * 端口
     */
    private Set<Room> room = new HashSet<>();
    /**
     * 最大每帧处理数据的长度
     */
    private Integer maxFramePayloadLength = 1048576;
    /**
     * 设置http交互最大内容长度
     */
    private Integer maxHttpContentLength = 1048576;

    /**
     * 是否使用epoll模式,默认false
     */
    private boolean useLinuxNativeEpoll;
    /**
     * socket连接数大小
     */
    private Integer bossCount = 1;
    /**
     * 工作线程
     */
    private Integer workCount = 1000;
    /**
     * 是否允许自定义请求
     */
    private boolean allowCustomRequests = true;
    /**
     * 协议升级超时时间（毫秒），默认10秒。HTTP握手升级为ws协议超时时间
     */
    private Integer upgradeTimeout = 10_000;
    /**
     * Ping消息超时时间（毫秒），默认60秒，这个时间间隔内没有接收到心跳消息就会发送超时事件
     */
    private Integer pingTimeout = 60_000;

    /**
     * Ping消息间隔（毫秒），默认25秒。客户端向服务器发送一条心跳消息间隔
     */
    private Integer pingInterval = 25_000;

    /**
     * 命名空间
     */
    private String namespace;

    /**
     * 认证工厂
     */
    private String authFactory = "com.chua.starter.service.configuration.AdminSocketAuthFactory";


    @Data
    public static class Room {

        /**
         * 端口
         */
        private int port;

        /**
         * 客户端ID
         */
        private String clientId;

        /**
         * 上下文路径
         */
        private String contextPath;
    }
}
