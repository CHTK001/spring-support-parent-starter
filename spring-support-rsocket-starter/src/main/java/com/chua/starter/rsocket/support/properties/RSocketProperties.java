package com.chua.starter.rsocket.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RSocket配置属性
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
@Data
@ConfigurationProperties(prefix = RSocketProperties.PRE, ignoreInvalidFields = true)
public class RSocketProperties {

    public static final String PRE = "plugin.rsocket";

    /**
     * 是否启用RSocket
     */
    private boolean enable = false;

    /**
     * 监听地址
     */
    private String host = "0.0.0.0";

    /**
     * 监听端口
     */
    private int port = 7000;

    /**
     * Boss线程数
     */
    private int bossCount = 1;

    /**
     * Worker线程数
     */
    private int workCount = 100;

    /**
     * 最大帧大小（字节）
     * <p>
     * 与maxFramePayloadLength作用相同，优先使用maxFrameSize
     */
    private int maxFrameSize = 1048576;

    /**
     * 最大帧载荷长度（字节）
     * <p>
     * 为了保持向后兼容，建议使用maxFrameSize
     */
    private int maxFramePayloadLength = 1048576;

    /**
     * 最大HTTP内容长度（字节）
     */
    private int maxHttpContentLength = 1048576;

    /**
     * Ping超时时间（毫秒）
     */
    private int pingTimeout = 60000;

    /**
     * Ping间隔（毫秒）
     */
    private int pingInterval = 25000;

    /**
     * 是否使用Linux Native Epoll
     */
    private boolean useLinuxNativeEpoll = false;

    /**
     * 是否允许自定义请求
     */
    private boolean allowCustomRequests = true;

    /**
     * 认证工厂类全限定名
     */
    private String authFactory = "com.chua.starter.rsocket.support.auth.DefaultRSocketAuthFactory";

    /**
     * 编解码器类型
     */
    private String codecType = "json";

    /**
     * 命名空间
     */
    private String namespace;
}

