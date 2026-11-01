package com.chua.starter.cloudflared.support;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cloudflare Tunnel（cloudflared）客户端配置。
 *
 * <p>通过 {@code plugin.cloudflared.*} 前缀配置。模块<strong>默认开启</strong>，
 * 只要 类路径 存在 cloudflared，Spring Boot 启动后会自动建立 Cloudflare Tunnel，
 * 将本机服务（{@code server.port}）暴露为公网 HTTPS 地址。</p>
 *
 * <h2>字段必填/选填说明</h2>
 * <table border="1">
 *   <tr><th>字段</th><th>必填</th><th>默认</th><th>说明</th></tr>
 *   <tr><td>enable</td><td>否</td><td>{@code true}</td><td>总开关。设为 false 完全关闭</td></tr>
 *   <tr><td>binary</td><td>否</td><td>自动定位</td><td>cloudflared 可执行文件路径；为空时自动定位（PATH / {@code CLOUDFLARED_BIN}）</td></tr>
 *   <tr><td>token</td><td>否</td><td>无</td><td>Zero Trust 面板生成的远程管理隧道 Token；为空时使用免账号 Quick Tunnel</td></tr>
 *   <tr><td>hostname</td><td>否</td><td>无</td><td>固定自定义域名（需账户 + DNS CNAME 路由）</td></tr>
 *   <tr><td>localUrl</td><td>否</td><td>{@code http://127.0.0.1:server.port}</td><td>本地待暴露的服务地址；默认自动指向 server.port</td></tr>
 *   <tr><td>noAutoUpdate</td><td>否</td><td>{@code true}</td><td>禁用 cloudflared 运行期自动更新</td></tr>
 *   <tr><td>logLevel</td><td>否</td><td>{@code info}</td><td>cloudflared 日志级别：debug / info / warn / error / fatal</td></tr>
 *   <tr><td>startTimeout</td><td>否</td><td>{@code 40s}</td><td>启动等待公网 URL 的超时</td></tr>
 *   <tr><td>failFast</td><td>否</td><td>{@code false}</td><td>隧道启动失败时是否中断应用；true 直接抛异常退出，false 只打日志</td></tr>
 * </table>
 *
 * <h2>最小启用示例</h2>
 * <pre>
 * # 方式一：application.yml（Quick Tunnel，免账号）
 * plugin:
 *   cloudflared:
 *     enable: true
 *
 * # 方式二：固定域名（需账户）
 * plugin:
 *   cloudflared:
 *     hostname: tunnel.example.com
 *
 * # 方式三：Zero Trust 远程管理隧道
 * plugin:
 *   cloudflared:
 *     token: ${CLOUDFLARE_TUNNEL_TOKEN}
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.cloudflared")
public class CloudflaredProperties {

    /**
     * 总开关。【选填】默认 {@code true}；设为 false 完全禁用。
     */
    private boolean enable = true;

    /**
     * cloudflared 可执行文件路径。【选填】默认自动定位；
     * 为空时按 CLOUDFLARED_BIN 环境变量 → PATH → 常见安装目录 顺序查找。
     */
    private String binary;

    /**
     * Zero Trust 远程管理隧道 Token。【选填】默认无；
     * 为空时使用免账号、免登录的 Quick Tunnel（随机 *.trycloudflare.com 域名）。
     */
    private String token;

    /**
     * 固定自定义域名。【选填】默认无；需该域名已添加到 Cloudflare 账户并配置 CNAME 路由。
     */
    private String hostname;

    /**
     * 本地待暴露的服务地址。【选填】默认 {@code http://127.0.0.1:server.port}。
     */
    private String localUrl;

    /**
     * 是否禁用 cloudflared 运行期自动更新。【选填】默认 {@code true}，
     * 避免运行期自更新导致隧道中断。
     */
    private boolean noAutoUpdate = true;

    /**
     * cloudflared 日志级别。【选填】默认 {@code info}；
     * 可选 debug / info / warn / error / fatal。
     */
    private String logLevel = "info";

    /**
     * 启动等待公网 URL 的超时。【选填】默认 40 秒。
     */
    private Duration startTimeout = Duration.ofSeconds(40);

    /**
     * 启动失败时是否中断应用。【选填】默认 {@code false}。
     * false 只打 ERROR 日志、Spring 继续启动；true 抛出异常中断启动。
     */
    private boolean failFast = false;

    /**
     * 启动时是否按本配置文件自动建立默认隧道。【选填】默认 {@code true}（保持原有行为）。
     * <p>设为 {@code false} 时模块（含管理接口与持久化映射）仍然启用，只是不在启动期
     * 自动为 {@code server.port} 建隧道 —— 适合「从页面按需映射端口」的部署方式。</p>
     */
    private boolean autoStart = true;

    /**
     * 启动时是否恢复上次标记为「自动启动」的持久化映射。【选填】默认 {@code true}。
     */
    private boolean autoStartStored = true;

    /**
     * 映射持久化目录。【选填】默认 {@code ~/.chua/tunnel/cloudflared}。
     * <p>一条映射一个 JSON 文件，不使用数据库；目录可指向挂载盘做备份。</p>
     */
    private String storeDir;
}