package com.chua.starter.socket.websocket.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * webSocket 配置属性
 * <p>
 * 配置前缀为 {@code plugin.websocket}（见 {@link #PREFIX}），经 {@code @EnableConfigurationProperties}
 * 在 {@code WebSocketAutoConfiguration} 中注册；总开关 {@code plugin.websocket.enable=false}（默认）时
 * 整个自动配置跳过，不注册端点与 Bean。
 * </p>
 * <p>完整 YAML 配置示例：</p>
 * <pre>{@code
 * plugin:
 *   websocket:
 *     enable: true                    # 总开关，默认 false，不开则整个模块不装配
 *     endpoints:                      # 端点路径列表，默认 ["/ws"]，与应用同端口
 *       - /ws
 *     allowed-origins:                # 允许的 Origin，默认 ["*"]；空列表时不设置跨域
 *       - "*"
 *     sock-js: false                  # 是否启用 SockJS 回退，默认 false
 *     heartbeat-interval: 25000       # SockJS 心跳间隔（毫秒），仅 sock-js=true 时生效，默认 25000
 *     max-message-size: 65536         # 文本/二进制帧缓冲上限（字节），默认 65536
 *     send-time-limit: 10000          # 发送超时（毫秒），默认 10000；会话空闲超时 = 2 × send-time-limit
 *     send-buffer-size-limit: 524288  # 发送缓冲上限（字节），默认 524288；当前自动装配未使用
 * }</pre>
 *
 * @since 2025-12-25
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = WebSocketProperties.PREFIX)
public class WebSocketProperties {

    /**
     * 前缀
    */
    public static final String PREFIX = "plugin.websocket";

    /**
     * 是否启用 WebSocket 自动配置
     * <p>
     * 默认 {@code false}；为 {@code false} 时整个自动配置跳过，不注册端点与 Bean。
     * </p>
     * <pre>{@code
     * plugin:
     *   websocket:
     *     enable: true
     * }</pre>
     */
    private boolean enable = false;

    /**
     * webSocket 端点路径列表
     * <p>
     * 默认 {@code ["/ws"]}；列表中的路径逐个注册到同一个委托处理器，与应用同端口。
     * 显式配置为空列表时不会注册任何端点（代码无回落值）。
     * </p>
     * <pre>{@code
     * plugin:
     *   websocket:
     *     endpoints:
     *       - /ws
     *       - /ws/v2
     * }</pre>
     */
    private List<String> endpoints = new ArrayList<>(List.of("/ws"));

    /**
     * 允许的源（跨域资源共享）
     * <p>
     * 默认 {@code ["*"]}；空列表时不调用 {@code setAllowedOrigins}，跨域策略交由容器默认处理。
     * </p>
     * <pre>{@code
     * plugin:
     *   websocket:
     *     allowed-origins:
     *       - "https://www.example.com"
     * }</pre>
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of("*"));

    /**
     * 是否启用 SockJS 支持
     * <p>
     * 默认 {@code false}；为 {@code true} 时端点以 SockJS 回退方式注册，
     * 并把 {@code heartbeat-interval} 写入 SockJS 心跳时间。
     * </p>
     * <pre>{@code
     * plugin:
     *   websocket:
     *     sock-js: true
     * }</pre>
     */
    private boolean sockJs = false;

    /**
     * 心跳间隔（毫秒），0 表示禁用
     * <p>
     * 默认 {@code 25000}；仅 {@code sock-js=true} 时透传给 SockJS {@code setHeartbeatTime}，
     * {@code sock-js=false} 时不生效。本模块不校验取值，{@code <=0} 原样透传（按 0 禁用心跳口径）。
     * </p>
     * <pre>{@code
     * plugin:
     *   websocket:
     *     heartbeat-interval: 25000
     * }</pre>
     */
    // [P3C A20 豁免]
    private long heartbeatInterval = 25000;

    /**
     * 消息大小限制（字节）
     * <p>
     * 默认 {@code 65536}；写入容器的文本/二进制帧缓冲上限（{@code ServletServerContainerFactoryBean}）。
     * 本模块不校验取值，{@code <=0} 原样透传给 Servlet 容器。
     * </p>
     * <pre>{@code
     * plugin:
     *   websocket:
     *     max-message-size: 65536
     * }</pre>
     */
    // [P3C A20 豁免]
    private int maxMessageSize = 65536;

    /**
     * 发送缓冲区大小限制（字节）
     * <p>
     * 默认 {@code 524288}；当前自动装配未消费该字段，为预留配置。
     * </p>
     * <pre>{@code
     * plugin:
     *   websocket:
     *     send-buffer-size-limit: 524288
     * }</pre>
     */
    // [P3C A20 豁免]
    private int sendBufferSizeLimit = 524288;

    /**
     * 发送超时时间（毫秒）
     * <p>
     * 默认 {@code 10000}；当前自动装配中用作会话空闲超时：
     * {@code maxSessionIdleTimeout = sendTimeLimit * 2}（默认 20000ms），未作为实际发送超时使用。
     * 本模块不校验取值，{@code <=0} 时计算结果原样写入容器，具体语义以 Servlet 容器实现为准。
     * </p>
     * <pre>{@code
     * plugin:
     *   websocket:
     *     send-time-limit: 10000
     * }</pre>
     */
    // [P3C A20 豁免]
    private int sendTimeLimit = 10000;

    /**
     * 获取允许的源
     *
     * @return 允许的源列表
     */
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * 设置允许的源
     *
     * @param allowedOrigins 允许的源列表
     */
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * 获取是否启用 SockJS
     *
     * @return 是否启用 SockJS
     */
    public boolean isSockJs() {
        return sockJs;
    }

    /**
     * 设置是否启用 SockJS
     *
     * @param sockJs 是否启用 SockJS
     */
    public void setSockJs(boolean sockJs) {
        this.sockJs = sockJs;
    }

    /**
     * 获取心跳间隔
     *
     * @return 心跳间隔（毫秒）
     */
    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * 设置心跳间隔
     *
     * @param heartbeatInterval 心跳间隔（毫秒）
     */
    public void setHeartbeatInterval(long heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    /**
     * 获取消息大小限制
     *
     * @return 消息大小限制（字节）
     */
    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    /**
     * 设置消息大小限制
     *
     * @param maxMessageSize 消息大小限制（字节）
     */
    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    /**
     * 获取发送超时时间
     *
     * @return 发送超时时间（毫秒）
     */
    public int getSendTimeLimit() {
        return sendTimeLimit;
    }

    /**
     * 设置发送超时时间
     *
     * @param sendTimeLimit 发送超时时间（毫秒）
     */
    public void setSendTimeLimit(int sendTimeLimit) {
        this.sendTimeLimit = sendTimeLimit;
    }

    /**
     * 获取端点列表
     *
     * @return 端点列表
     */
    public List<String> getEndpoints() {
        return endpoints;
    }

    /**
     * 设置端点列表
     *
     * @param endpoints 端点列表
     */
    public void setEndpoints(List<String> endpoints) {
        this.endpoints = endpoints;
    }
}
