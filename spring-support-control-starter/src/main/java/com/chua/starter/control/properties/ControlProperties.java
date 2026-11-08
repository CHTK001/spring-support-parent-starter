package com.chua.starter.control.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WebRTC 远程控制配置。
 *
 * <p>配置前缀 {@code control.webrtc}，与 {@code utils-support-webrtc-starter} 的
 * {@code WebRtcServer} 生命周期保持一致：</p>
 * <ul>
 *   <li>{@code control.webrtc.enabled} — 是否启用远程控制模块（默认 true）</li>
 *   <li>{@code control.webrtc.host} — 信令监听地址（默认 0.0.0.0）</li>
 *   <li>{@code control.webrtc.port} — 信令端口（默认 10000）</li>
 *   <li>{@code control.webrtc.auto-start} — 是否随应用启动信令服务（默认 true）</li>
 *   <li>{@code control.webrtc.frame-rate} — 桌面采集帧率（默认 15）</li>
 *   <li>{@code control.webrtc.width} — 采集最大宽度（默认 1280）</li>
 *   <li>{@code control.webrtc.height} — 采集最大高度（默认 720）</li>
 * </ul>
 *
 * <p>{@code host} / {@code port} 同时是 REST（{@code /control/**}）与 WebSocket
 * （{@code /webrtc}）的对外地址来源。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
@ConfigurationProperties(prefix = ControlProperties.PREFIX)
public class ControlProperties {

    /**
     * 配置前缀
     */
    public static final String PREFIX = "control.webrtc";

    /**
     * 是否启用远程控制模块
     */
    private boolean enabled = true;

    /**
     * 信令监听地址
     */
    private String host = "0.0.0.0";

    /**
     * 信令端口
     */
    private int port = 10000;

    /**
     * 是否随应用启动信令服务
     */
    private boolean autoStart = true;

    /**
     * 桌面采集帧率
     */
    private int frameRate = 15;

    /**
     * 采集最大宽度
     */
    private int width = 1280;

    /**
     * 采集最大高度
     */
    private int height = 720;
}
