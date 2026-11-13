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
     * 是否启用统一入口（关闭后既不注册 REST 接口，也不恢复自动启动的映射）。
     */
    private boolean enable = true;

    /**
     * 映射持久化根目录。
     *
     * <p>缺省为 {@code ~/.chua/tunnel}，与改造前一致：每个提供方一个子目录
     * （{@code ngrok/}、{@code cloudflared/}），已有映射文件无需搬迁。</p>
     */
    private String storeDir;

    /**
     * 对外暴露的提供方白名单；为空表示「容器里有几个就暴露几个」。
     *
     * <p>用于多实现共存时按环境裁剪：例如生产只允许 cloudflared，
     * 即使 classpath 里同时有 ngrok 实现也不会出现在接口与页面里。</p>
     */
    private List<String> providers = new ArrayList<>();

    /**
     * 是否在应用启动时恢复 {@code autoStart=true} 的映射。
     */
    private boolean restoreOnStartup = true;

    /**
     * SSE 状态推送间隔。
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
