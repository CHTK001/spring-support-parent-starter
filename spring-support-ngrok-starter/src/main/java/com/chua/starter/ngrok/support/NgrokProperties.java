package com.chua.starter.ngrok.support;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Ngrok 客户端配置。
 *
 * <p>通过 {@code plugin.ngrok.*} 前缀配置。模块<strong>默认开启</strong>，
 * 只要在 classpath 上 + 配置了 {@code plugin.ngrok.authtoken}（或环境变量
 * {@code NGROK_AUTHTOKEN}），Spring Boot 启动后会自动建立 ngrok 隧道，
 * 将本机服务（{@code server.port}）暴露到公网。</p>
 *
 * <h2>字段必填/选填说明</h2>
 * <table border="1">
 *   <tr><th>字段</th><th>必填</th><th>默认</th><th>说明</th></tr>
 *   <tr><td>enable</td><td>否</td><td>{@code true}</td><td>总开关。设为 false 完全关闭</td></tr>
 *   <tr><td>authtoken</td><td><strong>是</strong></td><td>无</td><td>ngrok authtoken；为空时回退读取环境变量 {@code NGROK_AUTHTOKEN}；两者都为空则启动报错</td></tr>
 *   <tr><td>metadata</td><td>否</td><td>无</td><td>会话元数据（ngrok 控制台可见）</td></tr>
 *   <tr><td>serverAddr</td><td>否</td><td>无</td><td>私有部署 ngrok 服务端地址（形如 {@code tunnel.example.com:443}）</td></tr>
 *   <tr><td>caCert</td><td>否</td><td>无</td><td>私有部署 CA 证书（classpath 资源路径，例如 {@code classpath:ngrok-ca.crt}）</td></tr>
 *   <tr><td>heartbeatInterval</td><td>否</td><td>30s（ngrok 默认）</td><td>心跳间隔</td></tr>
 *   <tr><td>heartbeatTolerance</td><td>否</td><td>60s（ngrok 默认）</td><td>心跳容忍</td></tr>
 *   <tr><td>type</td><td>否</td><td>{@code http}</td><td>隧道类型：{@code http} / {@code tcp} / {@code tls}</td></tr>
 *   <tr><td>mode</td><td>否</td><td>{@code forward}</td><td>{@code forward} 自动转发到本机 server.port；{@code listen} 仅暴露公网 URL</td></tr>
 *   <tr><td>domain</td><td>否</td><td>无</td><td>固定域名（http/tls；需 ngrok 面板预留）</td></tr>
 *   <tr><td>subdomain</td><td>否</td><td>无</td><td>子域名（http/tls；部分套餐已下线）</td></tr>
 *   <tr><td>remoteAddress</td><td>tcp 模式必填</td><td>无</td><td>TCP 远程地址（需 ngrok 面板 reserve），形如 {@code 1.tcp.ngrok.io:20000}</td></tr>
 *   <tr><td>scheme</td><td>否</td><td>{@code HTTPS}</td><td>http 模式协议方案</td></tr>
 *   <tr><td>compression</td><td>否</td><td>{@code false}</td><td>启用 HTTP 响应压缩</td></tr>
 *   <tr><td>basicAuth</td><td>否</td><td>无</td><td>HTTP BasicAuth，格式 {@code user:password}；与 {@code basicAuthUsername/ Password} 二选一</td></tr>
 *   <tr><td>basicAuthUsername</td><td>否</td><td>无</td><td>BasicAuth 用户名</td></tr>
 *   <tr><td>basicAuthPassword</td><td>否</td><td>无</td><td>BasicAuth 密码</td></tr>
 *   <tr><td>failFast</td><td>否</td><td>{@code false}</td><td>ngrok 连接失败时是否中断应用；true 直接抛异常退出，false 只打日志</td></tr>
 *   <tr><td>extraLocalPorts</td><td>否</td><td>无</td><td>额外本地端口（逗号分隔），会同时建立这些端口的 http 转发隧道</td></tr>
 * </table>
 *
 * <h2>最小启用示例</h2>
 * <pre>
 * # 方式一：application.yml
 * plugin:
 *   ngrok:
 *     authtoken: 2abc...xyz
 *
 * # 方式二：环境变量（推荐 — token 不入 git）
 * export NGROK_AUTHTOKEN=2abc...xyz
 * </pre>
 *
 * <h2>完整启用示例</h2>
 * <pre>
 * plugin:
 *   ngrok:
 *     enable: true
 *     authtoken: ${NGROK_AUTHTOKEN}
 *     metadata: my-spring-service
 *     type: http
 *     mode: forward
 *     domain: example.ngrok-free.app
 *     basicAuth: admin:secret123
 *     compression: true
 *     failFast: false
 *     extraLocalPorts: 8081,8082
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "plugin.ngrok")
public class NgrokProperties {

    /**
     * 总开关。【选填】默认 {@code true}；设为 false 完全禁用。
     */
    private boolean enable = true;

    /**
     * ngrok authtoken。【必填】为空时自动读取环境变量 {@code NGROK_AUTHTOKEN}；
     * 两者都为空且未禁用时启动期报错。
     */
    private String authtoken;

    /**
     * 会话元数据。【选填】默认无；ngrok 控制台可见。
     */
    private String metadata;

    /**
     * 私有部署 ngrok 服务端地址。【选填】默认无；公有云可不填。
     * 形如 {@code tunnel.example.com:443}。
     */
    private String serverAddr;

    /**
     * 私有部署 CA 证书路径。【选填】默认无；classpath 资源路径，
     * 例如 {@code classpath:ngrok-ca.crt}。
     */
    private String caCert;

    /**
     * 心跳间隔。【选填】默认 ngrok 默认值。
     */
    private Duration heartbeatInterval;

    /**
     * 心跳容忍。【选填】默认 ngrok 默认值。
     */
    private Duration heartbeatTolerance;

    /**
     * 隧道类型。【选填】默认 {@code http}；可选 {@code http} / {@code tcp} / {@code tls}。
     */
    private String type = "http";

    /**
     * 转发模式。【选填】默认 {@code forward}。
     * <ul>
     *   <li>{@code forward} — 自动把本机 server.port 的流量通过 ngrok 暴露</li>
     *   <li>{@code listen} — 仅暴露公网 URL，连接建立后由调用方处理</li>
     * </ul>
     */
    private String mode = "forward";

    /**
     * 自定义域名。【选填】默认无；http/tls 模式生效，需 ngrok 面板预留。
     */
    private String domain;

    /**
     * 子域名。【选填】默认无；http/tls 模式生效，部分套餐已下线该能力。
     */
    private String subdomain;

    /**
     * TCP 远程地址。【tcp 模式必填】默认无；形如 {@code 1.tcp.ngrok.io:20000}。
     */
    private String remoteAddress;

    /**
     * 协议方案（http 模式）。【选填】默认 {@code HTTPS}；可选 {@code HTTP} / {@code HTTPS}。
     */
    private String scheme = "HTTPS";

    /**
     * 启用 HTTP 响应压缩。【选填】默认 {@code false}。
     */
    private boolean compression = false;

    /**
     * HTTP BasicAuth 凭据。【选填】默认无；格式 {@code user:password}；
     * 与 {@link #basicAuthUsername}/{@link #basicAuthPassword} 二选一，
     * 同时配置时优先使用本字段。
     */
    private String basicAuth;

    /**
     * BasicAuth 用户名。【选填】默认无；与 {@link #basicAuthPassword} 配合使用。
     */
    private String basicAuthUsername;

    /**
     * BasicAuth 密码。【选填】默认无；与 {@link #basicAuthUsername} 配合使用。
     */
    private String basicAuthPassword;

    /**
     * 启动失败时是否中断应用。【选填】默认 {@code false}。
     * <ul>
     *   <li>{@code false} — 打印 ERROR 日志后继续启动 Spring（不阻塞业务）</li>
     *   <li>{@code true} — 抛出异常中断启动（fail-fast 模式）</li>
     * </ul>
     * 注意：仅 ngrok Session 连接失败 / 启动失败时生效；
     * authtoken 为空导致的硬错误无论 failFast 设置如何都会中断启动（必填项）。
     */
    private boolean failFast = false;

    /**
     * 额外本地端口列表。【选填】默认无；逗号分隔，例如 {@code 8081,8082}。
     * 为空时只转发 {@code server.port}；非空时会同时为这些端口建立 http 转发隧道。
     * 仅在 {@link #mode} = {@code forward} 且 {@link #type} = {@code http} 时生效。
     */
    private String extraLocalPorts;
}
