package com.chua.starter.ngrok.support;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Ngrok 客户端配置。
 *
 * <p>通过 {@code plugin.ngrok.*} 前缀配置。模块<strong>默认开启</strong>，
 * 只要在 类路径 上 + 配置了 {@code plugin.ngrok.authtoken}（或环境变量
 * {@code NGROK_AUTHTOKEN}），Spring Boot 启动后会自动建立 ngrok 隧道，
 * 将本机服务（{@code server.port}）暴露到公网。</p>
 *
 * <h2>字段必填/选填说明</h2>
 * <table border="1">
 *   <tr><th>字段</th><th>必填</th><th>默认</th><th>说明</th></tr>
 *   <tr><td>enable</td><td>否</td><td>{@code true}</td><td>总开关。设为 false 完全关闭</td></tr>
 *   <tr><td>authtoken</td><td><strong>是</strong></td><td>无</td><td>ngrok authtoken；为空时依次回退环境变量、系统属性 {@code NGROK_AUTHTOKEN}；全部为空则启动期隧道失败</td></tr>
 *   <tr><td>metadata</td><td>否</td><td>无</td><td>会话元数据（ngrok 控制台可见）；为空时隧道元数据使用内置基名 {@code spring-support-ngrok}</td></tr>
 *   <tr><td>serverAddr</td><td>否</td><td>无</td><td>私有部署 ngrok 服务端地址（形如 {@code tunnel.example.com:443}）</td></tr>
 *   <tr><td>caCert</td><td>否</td><td>无</td><td>私有部署 CA 证书（classpath 资源路径，例如 {@code classpath:ngrok-ca.crt}）</td></tr>
 *   <tr><td>heartbeatInterval</td><td>否</td><td>30s（ngrok 默认）</td><td>心跳间隔；未配置时不设置到客户端，使用 ngrok SDK 默认</td></tr>
 *   <tr><td>heartbeatTolerance</td><td>否</td><td>60s（ngrok 默认）</td><td>心跳容忍；未配置时不设置到客户端，使用 ngrok SDK 默认</td></tr>
 *   <tr><td>type</td><td>否</td><td>{@code http}</td><td>隧道类型：{@code http} / {@code tcp} / {@code tls}</td></tr>
 *   <tr><td>mode</td><td>否</td><td>{@code forward}</td><td>{@code forward} 自动转发到本机 server.port；{@code listen} 仅暴露公网 URL</td></tr>
 *   <tr><td>domain</td><td>否</td><td>无</td><td>固定域名（http/tls；需 ngrok 面板预留）</td></tr>
 *   <tr><td>subdomain</td><td>否</td><td>无</td><td>子域名（已下线能力，仅打印告警，不设置到 SDK）</td></tr>
 *   <tr><td>remoteAddress</td><td>tcp 模式必填</td><td>无</td><td>TCP 远程地址（需 ngrok 面板 reserve），形如 {@code 1.tcp.ngrok.io:20000}</td></tr>
 *   <tr><td>scheme</td><td>否</td><td>{@code HTTPS}</td><td>http 模式协议方案</td></tr>
 *   <tr><td>compression</td><td>否</td><td>{@code false}</td><td>启用 HTTP 响应压缩</td></tr>
 *   <tr><td>basicAuth</td><td>否</td><td>无</td><td>HTTP BasicAuth，格式 {@code user:password}；与 {@code basicAuthUsername}/{@code basicAuthPassword} 二选一，同时配置时优先</td></tr>
 *   <tr><td>basicAuthUsername</td><td>否</td><td>无</td><td>BasicAuth 用户名</td></tr>
 *   <tr><td>basicAuthPassword</td><td>否</td><td>无</td><td>BasicAuth 密码</td></tr>
 *   <tr><td>failFast</td><td>否</td><td>{@code false}</td><td>ngrok 连接失败时是否中断应用；true 直接抛异常退出，false 只打日志</td></tr>
 *   <tr><td>extraLocalPorts</td><td>否</td><td>无</td><td>额外本地端口（逗号分隔），会同时建立这些端口的 http 转发隧道</td></tr>
 *   <tr><td>autoStart</td><td>否</td><td>{@code true}</td><td>启动期是否用 server.port 自动建默认隧道；false 改由 /ms/tunnel 按需创建</td></tr>
 *   <tr><td>autoStartStored</td><td>否</td><td>{@code true}</td><td>启动时是否恢复标记为「自动启动」的持久化映射</td></tr>
 *   <tr><td>storeDir</td><td>否</td><td>无（缺省 {@code ~/.chua/tunnel/ngrok}）</td><td>映射持久化目录覆盖</td></tr>
 * </table>
 *
 * <h2>最小启用示例</h2>
 * <pre>
 * # 方式一：application.yml
 * plugin:
 *   ngrok:
 *     authtoken: your-ngrok-authtoken
 *
 * # 方式二：环境变量（推荐 — token 不入 git）
 * export NGROK_AUTHTOKEN=your-ngrok-authtoken
 * </pre>
 *
 * <h2>完整启用示例</h2>
 * <pre>
 * plugin:
 *   ngrok:
 *     enable: true                              # 总开关，false 完全禁用自动配置
 *     authtoken: ${NGROK_AUTHTOKEN}             # authtoken；也可直接写值或依赖环境变量
 *     metadata: my-spring-service               # 会话元数据，ngrok 控制台可见
 *     server-addr: tunnel.example.com:443       # 私有部署服务端地址（公有云不填）
 *     ca-cert: classpath:ngrok-ca.crt           # 私有部署 CA 证书（与 server-addr 配套）
 *     heartbeat-interval: 30s                   # 心跳间隔（未配置用 SDK 默认）
 *     heartbeat-tolerance: 60s                  # 心跳容忍（未配置用 SDK 默认）
 *     type: http                                # http / tcp / tls
 *     mode: forward                             # forward 转发本机端口 / listen 仅暴露 URL
 *     domain: example.ngrok-free.app            # 固定域名（需 ngrok 面板预留）
 *     scheme: HTTPS                             # http 协议方案 HTTP / HTTPS
 *     compression: true                         # 启用 HTTP 响应压缩
 *     basic-auth: "your-username:your-password" # BasicAuth，也可拆成下面两个字段
 *     basic-auth-username: your-username        # BasicAuth 用户名
 *     basic-auth-password: your-password        # BasicAuth 密码
 *     fail-fast: false                          # true 时隧道失败中断应用启动
 *     extra-local-ports: 8081,8082              # 额外转发的本机端口（http+forward 生效）
 *     auto-start: true                          # 启动期自动建默认隧道
 *     auto-start-stored: true                   # 恢复持久化的自动启动映射
 *     store-dir: /data/tunnel/ngrok             # 映射持久化目录（缺省 ~/.chua/tunnel/ngrok）
 * </pre>
 *
 * <h2>按需映射示例（关闭启动期默认隧道）</h2>
 * <pre>
 * plugin:
 *   ngrok:
 *     authtoken: ${NGROK_AUTHTOKEN}
 *     auto-start: false        # 启动期不自动建隧道，改由 /ms/tunnel 按需创建
 *     auto-start-stored: true  # 仍恢复上次标记为自动启动的持久化映射
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.ngrok")
public class NgrokProperties {

    /**
     * 总开关。【选填】默认 {@code true}；设为 false 完全禁用。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     enable: true
     * </pre>
     */
    private boolean enable = true;

    /**
     * ngrok authtoken。【必填】解析优先级：配置值 → 环境变量 {@code NGROK_AUTHTOKEN}
     * → 系统属性 {@code NGROK_AUTHTOKEN}；全部为空且未禁用时启动期报错。
     *
     * <p>YAML 示例（推荐环境变量注入，避免 token 入库）：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     authtoken: ${NGROK_AUTHTOKEN}
     * </pre>
     */
    private String authtoken;

    /**
     * 会话元数据。【选填】默认无；ngrok 控制台可见。
     * 为空时隧道元数据使用内置基名 {@code spring-support-ngrok}。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     metadata: my-spring-service
     * </pre>
     */
    private String metadata;

    /**
     * 私有部署 ngrok 服务端地址。【选填】默认无（连 ngrok 官方边缘节点）；公有云可不填。
     * 形如 {@code tunnel.example.com:443}。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     server-addr: tunnel.example.com:443
     * </pre>
     */
    private String serverAddr;

    /**
     * 私有部署 CA 证书路径。【选填】默认无；类路径 资源路径，
     * 例如 {@code classpath:ngrok-ca.crt}（读取失败会回退按文件路径读取）。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     ca-cert: classpath:ngrok-ca.crt
     * </pre>
     */
    private String caCert;

    /**
     * 心跳间隔。【选填】字段为 {@code null}（未配置）时不设置到客户端，
     * 使用 ngrok SDK 默认值（约 30s）。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     heartbeat-interval: 30s
     * </pre>
     */
    private Duration heartbeatInterval;

    /**
     * 心跳容忍。【选填】字段为 {@code null}（未配置）时不设置到客户端，
     * 使用 ngrok SDK 默认值（约 60s）。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     heartbeat-tolerance: 60s
     * </pre>
     */
    private Duration heartbeatTolerance;

    /**
     * 隧道类型。【选填】默认 {@code http}；可选 {@code http} / {@code tcp} / {@code tls}。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     type: http
     * </pre>
     */
    private String type = "http";

    /**
     * 转发模式。【选填】默认 {@code forward}。
     * <ul>
     *   <li>{@code forward} — 自动把本机 server.port 的流量通过 ngrok 暴露</li>
     *   <li>{@code listen} — 仅暴露公网 URL，连接建立后由调用方处理</li>
     * </ul>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     mode: forward
     * </pre>
     */
    private String mode = "forward";

    /**
     * 自定义域名。【选填】默认无；http/tls 模式生效，需 ngrok 面板预留。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     domain: example.ngrok-free.app
     * </pre>
     */
    private String domain;

    /**
     * 子域名。【选填】默认无；已下线能力——配置后仅打印告警，
     * 不会设置到 SDK，请改用 domain。
     *
     * <p>YAML 示例（仅为兼容保留，实际不生效）：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     subdomain: my-sub
     * </pre>
     */
    private String subdomain;

    /**
     * TCP 远程地址。【tcp 模式必填】默认无；形如 {@code 1.tcp.ngrok.io:20000}。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     remote-address: 1.tcp.ngrok.io:20000
     * </pre>
     */
    private String remoteAddress;

    /**
     * 协议方案（http 模式）。【选填】默认 {@code HTTPS}；可选 {@code HTTP} / {@code HTTPS}。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     scheme: HTTPS
     * </pre>
     */
    private String scheme = "HTTPS";

    /**
     * 启用 HTTP 响应压缩。【选填】默认 {@code false}。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     compression: true
     * </pre>
     */
    private boolean compression = false;

    /**
     * HTTP 基础认证 凭据。【选填】默认无；格式 {@code user:password}；
     * 与 {@link #basicAuthUsername}/{@link #basicAuthPassword} 二选一，
     * 同时配置时优先使用本字段。
     *
     * <p>YAML 示例（ngrok 要求密码 8~128 位）：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     basic-auth: "your-username:your-password"
     * </pre>
     */
    private String basicAuth;

    /**
     * 基础认证 用户名。【选填】默认无；与 {@link #basicAuthPassword} 配合使用。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     basic-auth-username: your-username
     * </pre>
     */
    private String basicAuthUsername;

    /**
     * 基础认证 密码。【选填】默认无；与 {@link #basicAuthUsername} 配合使用。
     *
     * <p>YAML 示例（ngrok 要求密码 8~128 位）：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     basic-auth-password: your-password
     * </pre>
     */
    private String basicAuthPassword;

    /**
     * 启动失败时是否中断应用。【选填】默认 {@code false}。
     * <ul>
     *   <li>{@code false} — 打印 ERROR 日志后继续启动 Spring（不阻塞业务）</li>
     *   <li>{@code true} — 抛出异常中断启动（fail-fast 模式）</li>
     * </ul>
     * 注意：启动期隧道失败（含 authtoken 未配置、连接失败、tcp 缺
     * remote-address 等）统一走该开关；{@code enable=false} 或
     * {@code auto-start=false} 时不建隧道，本开关不参与判定。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     fail-fast: false
     * </pre>
     */
    private boolean failFast = false;

    /**
     * 额外本地端口列表。【选填】默认无；逗号分隔，例如 {@code 8081,8082}。
     * 为空时只转发 {@code server.port}；非空时会同时为这些端口建立 http 转发隧道
     * （非法端口忽略并打印告警）。
     * 仅在 {@link #mode} = {@code forward} 且 {@link #type} = {@code http} 时生效。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     extra-local-ports: 8081,8082
     * </pre>
     */
    private String extraLocalPorts;

    /**
     * 启动时是否按本配置文件自动建立默认隧道。【选填】默认 {@code true}（保持原有行为）。
     * <p>设为 {@code false} 时模块（含管理接口与持久化映射）仍然启用，只是不在启动期
     * 用 {@code server.port} 自动建隧道 —— 适合「从页面按需映射端口」的部署方式。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     auto-start: true
     * </pre>
     */
    private boolean autoStart = true;

    /**
     * 启动时是否恢复上次标记为「自动启动」的持久化映射。【选填】默认 {@code true}。
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     auto-start-stored: true
     * </pre>
     */
    private boolean autoStartStored = true;

    /**
     * 映射持久化目录。【选填】字段为空时由 tunnel-starter 使用缺省目录
     * {@code ~/.chua/tunnel/ngrok}。
     * <p>一条映射一个 JSON 文件，不使用数据库；目录可指向挂载盘做备份。</p>
     *
     * <p>YAML 示例：</p>
     * <pre>
     * plugin:
     *   ngrok:
     *     store-dir: /data/tunnel/ngrok
     * </pre>
     */
    private String storeDir;
}
