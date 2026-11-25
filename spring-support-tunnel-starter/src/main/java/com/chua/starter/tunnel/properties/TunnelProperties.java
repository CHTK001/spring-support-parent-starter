package com.chua.starter.tunnel.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 内网穿透统一入口配置（{@code plugin.tunnel}）。
 *
 * <p>与各提供方自己的配置（{@code plugin.ngrok} / {@code plugin.cloudflared}）
 * 分工明确：本配置只管「统一入口开不开、映射存哪、对外暴露哪些提供方」，
 * 凭据、二进制路径等厂商细节仍留在各自 starter 的配置里。</p>
 *
 * <p>完整配置示例（{@code application.yml}）：</p>
 *
 * <pre>
 * plugin:
 *   tunnel:
 *     enable: true                            # 总开关，默认 true
 *     store-dir: /data/app/tunnel             # 映射持久化根目录，默认 ~/.chua/tunnel
 *     providers:                              # 对外暴露的提供方白名单，留空 = 全部暴露
 *       - cloudflared
 *     instance-url: http://192.168.1.10:8080  # 本实例控制地址（owner 标记），默认按 server.port 推导
 *     restore-on-startup: true                # 应用就绪时是否恢复 autoStart=true 的映射
 *     sse-interval: 1500ms                    # SSE 状态推送间隔，内部下限 500ms
 * </pre>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Data
@ConfigurationProperties(prefix = TunnelProperties.PREFIX)
public class TunnelProperties {

    /**
     * 配置前缀。
     */
    public static final String PREFIX = "plugin.tunnel";

    /**
     * 是否启用统一入口。
     *
     * <p>{@code false} 时不注册 {@code /ms/tunnel/**} REST 接口与编排服务，
     * 应用就绪后也不再恢复自动启动的映射；各提供方 starter 自身的一次性隧道
     * 能力不受影响。</p>
     *
     * <p>默认值：{@code true}（未配置同视为开启，{@code matchIfMissing=true}）。</p>
     *
     * <pre>
     * plugin:
     *   tunnel:
     *     enable: true
     * </pre>
     */
    private boolean enable = true;

    /**
     * 映射持久化根目录：一条映射一个 JSON（{@code <store-dir>/<provider>/<id>.json}），
     * 运行锁文件 {@code <id>.lock} 同样写在这里；单个提供方可通过
     * {@code TunnelProvider#storeDir()} 覆盖自己的目录。
     *
     * <p>默认值：未配置或空白时回落为 {@code ~/.chua/tunnel}
     * （见 {@link #resolveStoreDir()}，取 {@code user.home/.chua/tunnel}）；
     * 每个提供方一个子目录，已有映射文件无需搬迁。</p>
     *
     * <pre>
     * plugin:
     *   tunnel:
     *     store-dir: /data/app/tunnel
     * </pre>
     */
    private String storeDir;

    /**
     * 对外暴露的提供方白名单；为空表示「容器里有几个就暴露几个」。
     *
     * <p>用于多实现共存时按环境裁剪：例如生产只允许 cloudflared，
     * 即使 classpath 里同时有 ngrok 实现也不会出现在接口与页面里。
     * 名单大小写不敏感、自动去空白；{@code name()} 不在名单内的实现
     * 不会出现在 {@code /ms/tunnel/providers} 与页面中。</p>
     *
     * <p>默认值：空列表（不裁剪）。</p>
     *
     * <pre>
     * plugin:
     *   tunnel:
     *     providers:
     *       - cloudflared
     * </pre>
     */
    private List<String> providers = new ArrayList<>();

    /**
     * 本实例的控制地址（{@code http://host:port}），用作隧道记录的所有权标记。
     *
     * <p>多实例共享同一映射目录时，启动/停止会依据该地址互相识别与接管：
     * 手动启动被占用的隧道时，先请求 owner 实例停止该隧道再接管
     * （约 6s 等待窗口）。多实例共享目录时必须为每个实例配置不同且
     * 互相可达的地址。</p>
     *
     * <p>默认值：未配置或空白时回落为 {@code http://127.0.0.1:{server.port}}
     * （端口缺省 8080，由自动装配按 {@code server.port} 推导）；
     * 自动装配下恒有值，不会因此关闭所有权机制。</p>
     *
     * <pre>
     * plugin:
     *   tunnel:
     *     instance-url: http://192.168.1.10:8080
     * </pre>
     */
    private String instanceUrl;

    /**
     * 是否在应用启动时恢复 {@code autoStart=true} 的映射。
     *
     * <p>恢复仅针对 {@code enabled=true} 且 {@code autoStart=true}、提供方
     * {@code autoStartStored()} 未关闭的映射，且以「不接管」方式执行——
     * 同一条隧道已被其他实例运行时跳过，避免实例间互抢域名。</p>
     *
     * <p>默认值：{@code true}；不希望重启后自动占用公网域名时设为 {@code false}。</p>
     *
     * <pre>
     * plugin:
     *   tunnel:
     *     restore-on-startup: true
     * </pre>
     */
    private boolean restoreOnStartup = true;

    /**
     * SSE 状态推送间隔：{@code GET /ms/tunnel/stream} 广播 {@code tunnels}
     * 事件的周期。
     *
     * <p>默认值：{@code 1500ms}；配置为 {@code null} 时控制器同样回落 1500ms。
     * {@code TunnelStatusStream} 内部下限 500ms（{@code Math.max(500, interval)}），
     * 配置小于 500ms 按 500ms 生效。</p>
     *
     * <pre>
     * plugin:
     *   tunnel:
     *     sse-interval: 1500ms
     * </pre>
     */
    // [P3C A20 豁免]
    private Duration sseInterval = Duration.ofMillis(1500L);

    /**
     * 解析持久化根目录。
     *
     * @return 绝对路径字符串
     */
    public String resolveStoreDir() {
        if (storeDir != null && !storeDir.isBlank()) {
            return storeDir.trim();
        }
        return java.nio.file.Path.of(System.getProperty("user.home"), ".chua", "tunnel").toString();
    }
}
