package com.chua.socketio.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.chua.socketio.support.properties.SocketIoProperties.PRE;

/**
 * socket.io
 *
 * @author CH
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
     * 是否开启
     */
    private boolean open;
    /**
     * 本地IP
     */
    private String host = "0.0.0.0";
    /**
     * 端口
     */
    private Integer port = 31256;
    /**
     * 最大每帧处理数据的长度
     */
    private Integer maxFramePayloadLength = 1048576;
    /**
     * 设置http交互最大内容长度
     */
    private Integer maxHttpContentLength = 1048576;
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
}
