package com.chua.rsocket.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.rsocket.support.properties.RSocketProperties.PRE;

/**
 * RSocket 配置属性类
 * 用于配置RSocket服务器的各项参数
 *
 * @author CH
 * @version 1.0.0
 * @since 2024-12-04
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class RSocketProperties {

    public static final String PRE = "plugin.rsocket";

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
    private int port = 7000;

    /**
     * 编解码器类型
     */
    private String codecType = "json";

    /**
     * 是否开启数据加密（默认关闭）
     */
    private boolean encryptEnabled = false;

    /**
     * 加密密钥（可选）
     */
    private String encryptKey;

    /**
     * 最大帧大小（字节），默认1MB
     */
    private int maxFrameSize = 1048576;

    /**
     * 最大每帧处理数据的长度
     */
    private int maxFramePayloadLength = 1048576;

    /**
     * 是否使用epoll模式，默认false
     */
    private boolean useLinuxNativeEpoll;

    /**
     * Boss线程数
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
     * Ping消息超时时间（毫秒），默认60秒
     */
    private int pingTimeout = 60_000;

    /**
     * Ping消息间隔（毫秒），默认25秒
     */
    private int pingInterval = 25_000;

    /**
     * 认证工厂类名
     */
    private String authFactory;
}
