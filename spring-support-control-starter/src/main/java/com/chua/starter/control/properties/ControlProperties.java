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
 *   <li>{@code control.webrtc.token} — 信令令牌；为空启动时自动生成（默认空串）</li>
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
 * <p>完整配置示例（application.yml）：</p>
 * <pre>{@code
 * control:
 *   webrtc:
 *     enabled: true        # 总开关，false 时整模块不装配（默认 true）
 *     token: ""            # 信令令牌，留空则启动时自动生成（默认空串）
 *     host: 0.0.0.0        # 信令监听地址（默认 0.0.0.0）
 *     port: 10000          # 信令端口，独立于 server.port（默认 10000）
 *     auto-start: true     # 是否随应用启动信令服务（默认 true）
 *     frame-rate: 15       # 桌面采集帧率（默认 15）
 *     width: 1280          # 采集最大宽度（默认 1280）
 *     height: 720          # 采集最大高度（默认 720）
 * }</pre>
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
     * 是否启用远程控制模块。
     *
     * <p>含义：模块总开关。{@code false} 时整个自动配置被短路——{@code WebRtcServer}
     * Bean 与本配置类的绑定都不会发生，{@code /webrtc} 信令端口也不绑定；此时通过
     * 组件扫描 / {@code @Import} 注册的 {@code ControlController} 会因缺少依赖而
     * 启动失败，需一并移除注册。</p>
     *
     * <p>默认值：{@code true}；未配置时同样视为开启（自动配置为
     * {@code matchIfMissing = true}）。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * control:
     *   webrtc:
     *     enabled: true
     * }</pre>
     */
    private boolean enabled = true;

    /**
     * 信令访问令牌。
     *
     * <p>含义：保护 WebSocket 信令握手（{@code ws://host:port/webrtc?token=...}），
     * 不匹配时握手被拒（403）。令牌只通过已登录的 {@code GET /control/config}
     * 下发给前端，页面连接时再拼到 ws 地址上。</p>
     *
     * <p>默认值：空字符串 {@code ""}；为空（null 或空白）时启动阶段自动生成
     * 32 位 UUID（去横线）并回写到本配置，仅内存态、不持久化，重启即变化。
     * 生产环境建议显式配置固定的高强度随机串（占位示例：{@code change-me}）。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * control:
     *   webrtc:
     *     token: change-me-to-a-long-random-string
     * }</pre>
     */
    private String token = "";

    /**
     * 信令监听地址。
     *
     * <p>含义：信令服务（{@code /webrtc} WebSocket）的绑定地址。</p>
     *
     * <p>默认值：{@code 0.0.0.0}（监听全部网卡）。注意 {@code GET /control/config}
     * 下发的 {@code wsUrl} 使用当前请求的 Host 拼接，不会把 0.0.0.0 直接给浏览器。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * control:
     *   webrtc:
     *     host: 0.0.0.0
     * }</pre>
     */
    private String host = "0.0.0.0";

    /**
     * 信令端口。
     *
     * <p>含义：信令服务监听端口，独立于 Spring Boot 的 {@code server.port}。</p>
     *
     * <p>默认值：{@code 10000}。本模块不对 {@code <=0} 等非法值做校验或回落，
     * 按原值装配；端口被占用或不可绑定时信令启动失败仅记录 error 日志，
     * 应用继续启动（远控降级为不可用）。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * control:
     *   webrtc:
     *     port: 10000
     * }</pre>
     */
    // [P3C A20 豁免]
    private int port = 10000;

    /**
     * 是否随应用启动信令服务。
     *
     * <p>含义：{@code true} 时自动配置在装配 {@code WebRtcServer} 后立即调用
     * {@code start()} 绑定信令端口；{@code false} 时 Bean 已创建但不启动，
     * 需要使用方在合适时机自行调用 {@code start()}。</p>
     *
     * <p>默认值：{@code true}。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * control:
     *   webrtc:
     *     auto-start: true
     * }</pre>
     */
    private boolean autoStart = true;

    /**
     * 桌面采集帧率。
     *
     * <p>含义：桌面画面采集帧率（FPS），装配时经
     * {@code ScreenCaptureService#setCaptureRate(int)} 下发给采集服务，
     * 设置失败仅记录 warn 日志、不阻断启动。</p>
     *
     * <p>默认值：{@code 15}。本模块不对 {@code <=0} 做校验或回落，按原值下发。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * control:
     *   webrtc:
     *     frame-rate: 15
     * }</pre>
     */
    // [P3C A20 豁免]
    private int frameRate = 15;

    /**
     * 采集最大宽度。
     *
     * <p>含义：桌面采集的最大宽度参考值。</p>
     *
     * <p>默认值：{@code 1280}。当前仅随 {@code GET /control/config} 下发给前端
     * 作为参考，模块内未参与采集参数设置。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * control:
     *   webrtc:
     *     width: 1280
     * }</pre>
     */
    // [P3C A20 豁免]
    private int width = 1280;

    /**
     * 采集最大高度。
     *
     * <p>含义：桌面采集的最大高度参考值。</p>
     *
     * <p>默认值：{@code 720}。当前仅随 {@code GET /control/config} 下发给前端
     * 作为参考，模块内未参与采集参数设置。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * control:
     *   webrtc:
     *     height: 720
     * }</pre>
     */
    // [P3C A20 豁免]
    private int height = 720;
}
