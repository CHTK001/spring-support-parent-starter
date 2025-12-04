package com.chua.socket.support.properties;

import com.chua.socket.support.SocketProtocol;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
     * 协议类型，默认 socketio
     * 可选值：socketio, rsocket
     */
    private SocketProtocol protocol = SocketProtocol.SOCKETIO;

    /**
     * 本地IP
     */
    private String host = "0.0.0.0";

    /**
     * 端口
     */
    private int port = 9000;

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
}
