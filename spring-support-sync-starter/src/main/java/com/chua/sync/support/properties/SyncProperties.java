package com.chua.sync.support.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.chua.sync.support.properties.SyncProperties.PRE;

/**
 * 同步协议配置
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/04
 */
@Data
@ConfigurationProperties(prefix = PRE, ignoreInvalidFields = true)
public class SyncProperties {

    public static final String PRE = "plugin.sync";

    /**
     * 是否启用同步协议
     */
    private boolean enable = false;

    /**
     * 程序类型：server-服务端，client-客户端
     */
    private String type = "client";

    /**
     * 协议类型：rsocket、websocket-sync 等
     * 支持的协议类型取决于 utils-support-common-starter 中的 SPI 实现
     */
    private String protocol = "rsocket";

    /**
     * 服务端主机地址（当type=server时生效）
     */
    private String serverHost = "0.0.0.0";

    /**
     * 服务端端口（当type=server时生效）
     */
    private int serverPort = 19380;

    /**
     * 服务端地址（当type=client时生效）
     * 格式：ws://host:port 或 wss://host:port
     */
    private String serverAddress = "ws://localhost:19380";

    /**
     * 心跳开关
     */
    private boolean heartbeat = true;

    /**
     * 心跳间隔（秒）
     */
    private int heartbeatInterval = 30;

    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout = 10000;

    /**
     * 重连间隔（秒）
     */
    private int reconnectInterval = 5;

    /**
     * 最大重连次数，-1表示无限重连
     */
    private int maxReconnectAttempts = -1;

    /**
     * 主题与处理器映射
     * <p>
     * key: 主题名称
     * value: SPI 接口名称（用于查找 SyncMessageHandler 实现）
     * </p>
     * 示例配置：
     * <pre>
     * plugin:
     *   sync:
     *     topics:
     *       user/login: userLoginHandler
     *       order/create: orderCreateHandler
     *       system/notify: systemNotifyHandler
     * </pre>
     */
    private Map<String, String> topics = new LinkedHashMap<>();

    /**
     * 定时同步配置
     */
    private ScheduleSync scheduleSync = new ScheduleSync();

    /**
     * 定时同步配置类
     */
    @Data
    public static class ScheduleSync {

        /**
         * 是否启用定时同步
         */
        private boolean enable = false;

        /**
         * 同步间隔时间（秒）
         */
        private int interval = 300;

        /**
         * 初始延迟时间（秒）
         */
        private int initialDelay = 60;
    }
}
