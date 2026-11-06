// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.tailscale.support;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Tailscale Funnel 客户端配置。
 *
 * <p>通过 {@code plugin.tailscale.*} 前缀配置。模块<strong>默认开启</strong>，
 * 只要 classpath 上存在 tailscale 可执行文件，Spring Boot 启动后会自动建立
 * Tailscale Funnel，将本机服务（{@code server.port}）暴露为公网地址。</p>
 *
 * <h2>字段必填/选填说明</h2>
 * <table border="1">
 *   <tr><th>字段</th><th>必填</th><th>默认</th><th>说明</th></tr>
 *   <tr><td>enable</td><td>否</td><td>{@code true}</td><td>总开关。设为 false 完全关闭</td></tr>
 *   <tr><td>binary</td><td>否</td><td>自动定位</td><td>tailscale 可执行文件路径；为空时自动定位（PATH / {@code TAILSCALE_BIN}）</td></tr>
 *   <tr><td>authKey</td><td>否</td><td>无</td><td>设备认证 Key（{@code TS_AUTHKEY}），用于无登录态建隧道；非键入时设备需先 {@code tailscale up} 加入 tailnet</td></tr>
 *   <tr><td>startTimeout</td><td>否</td><td>{@code 60s}</td><td>启动等待公网 URL 的超时</td></tr>
 *   <tr><td>failFast</td><td>否</td><td>{@code false}</td><td>隧道启动失败时是否中断应用；true 直接抛异常退出，false 只打日志</td></tr>
 *   <tr><td>autoStart</td><td>否</td><td>{@code true}</td><td>启动时是否按本配置自动建立默认隧道</td></tr>
 *   <tr><td>autoStartStored</td><td>否</td><td>{@code true}</td><td>启动时是否恢复上次标记为「自动启动」的持久化映射</td></tr>
 *   <tr><td>storeDir</td><td>否</td><td>{@code ~/.chua/tunnel/tailscale}</td><td>映射持久化目录；一条映射一个 JSON 文件，不使用数据库</td></tr>
 * </table>
 *
 * <h2>TCP 模式说明</h2>
 * <p>Tailscale Funnel 只能暴露 {@code 443 / 8443 / 10000} 三个公网 TCP 端口（未提权的设备），
 * 因此 {@code tcp} 模式下映射固定暴露 {@code 443}（提供者常量 {@code TCP_PORTS} 的首项），
 * 一律使用 {@code tailscale funnel --tcp=443 --yes <target>} 发起；本地服务由 {@code localPort} 决定。</p>
 *
 * <h2>最小启用示例</h2>
 * <pre>
 * # 方式一：最低配（设备已通过 tailscale up 加入 tailnet）
 * plugin:
 *   tailscale:
 *     enable: true
 *
 * # 方式二：无登录态一键建隧道（authKey 推荐走环境变量）
 * plugin:
 *   tailscale:
 *     auth-key: ${TAILSCALE_AUTHKEY}
 * </pre>
 *
 * <h2>启用方式</h2>
 * <pre>
 * # 方式三：任意环境统一配置
 * plugin:
 *   tailscale:
 *     binary: /usr/local/bin/tailscale
 *     start-timeout: 90s
 * </pre>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.tailscale")
public class TailscaleProperties {

    /**
     * 认证 Key 环境变量名（写入子进程环境，同 {@code tailnet up --auth-key}）。
     */
    private static final String ENV_AUTH_KEY = "TS_AUTHKEY";

    /**
     * 总开关。【选填】默认 {@code true}；设为 false 完全禁用。
     */
    private boolean enable = true;

    /**
     * tailscale 可执行文件路径。【选填】默认自动定位；
     * 为空时按 TAILSCALE_BIN 环境变量 → PATH → 常见安装目录 顺序查找。
     */
    private String binary;

    /**
     * 设备认证 Key。【选填】默认无；
     * 可通过环境变量 {@code TS_AUTHKEY} 设置，自动注入子进程环境。
     * 依赖设备先加入 tailnet（{@code tailscale up}）时可不填。
     */
    private String authKey;

    /**
     * 启动等待公网 URL 的超时。【选填】默认 60 秒。
     */
    private Duration startTimeout = Duration.ofSeconds(60);

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
     * 映射持久化目录。【选填】默认 {@code ~/.chua/tunnel/tailscale}。
     * <p>一条映射一个 JSON 文件，不使用数据库；目录可指向挂载盘做备份。</p>
     */
    private String storeDir;

    /**
     * 解析认证 Key：映射记录未提供专用字段，Key 是设备级凭据，
     * 因此按「配置文件 → 环境变量」的优先级解析。
     *
     * @return Key，未配置时返回 null
     */
    public String resolveAuthKey() {
        String key = StringUtils.hasText(getAuthKey()) ? getAuthKey().trim() : null;
        if (!StringUtils.hasText(key)) {
            key = StringUtils.hasText(System.getenv(ENV_AUTH_KEY))
                    ? System.getenv(ENV_AUTH_KEY).trim() : null;
        }
        return key;
    }
}
