// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.tailscale.support;

import com.chua.tailscale.support.TailscaleTunnelClient;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Tailscale Funnel 自动配置：Spring Boot 启动后自动建立 Tailscale Funnel，
 * 将本机服务暴露到公网。
 *
 * <p>触发时机：{@link ApplicationReadyEvent}（此时 {@code server.port} 已确定）
 * -> 建立 Funnel 输出公网地址。</p>
 *
 * <p>关闭时机：{@link ContextClosedEvent} -> 优雅关闭 tailscale 进程。</p>
 *
 * <p>支持两种隧道模式：</p>
 * <ul>
 *   <li>HTTP（默认）：{@code tailscale funnel --yes <local>}，
 *   输出 HTTPS 地址（如 {@code https://<machine>.<tailnet>.ts.net}）</li>
 *   <li>TCP：{@code tailscale funnel --tcp=443 --yes <local>}，
 *   只能暴露 {@code 443 / 8443 / 10000} 端口。</li>
 * </ul>
 *
 * <p><b>注意：</b>Tailscale 没有单条隧道的 Token，设备必须先加入 Tailnet
 * （{@code tailscale up} 或 {@code plugin.tailscale.authKey}），
 * 否则 {@code funnel} 会报错 "failed to connect to local tailscaled process"
 * 或 "please run tailscale up"，该错误会被登记在映射上。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(TailscaleTunnelClient.class)
@ConditionalOnProperty(prefix = "plugin.tailscale", name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TailscaleProperties.class)
public class TailscaleAutoConfiguration {

    /**
     * 当前已建立的 Funnel 客户端。
     */
    private TailscaleTunnelClient client;

    /**
     * 配置属性。
     */
    private final TailscaleProperties properties;

    /**
     * 创建 Tailscale 自动配置实例。
     *
     * @param properties 配置属性
     */
    public TailscaleAutoConfiguration(TailscaleProperties properties) {
        this.properties = properties;
    }

    /**
     * 在应用就绪后建立 Tailscale Funnel。
     *
     * @param event 应用就绪事件
     */
    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (client != null) {
            return;
        }
        if (!properties.isAutoStart()) {
            log.info("[Tailscale] plugin.tailscale.auto-start=false，跳过启动期默认隧道（管理接口仍可用）");
            return;
        }
        int localPort = resolveLocalPort(event);
        if (localPort <= 0) {
            log.warn("[Tailscale] 无法解析本地 server.port，跳过隧道建立");
            return;
        }
        try {
            startTunnel(localPort);
        } catch (Exception e) {
            handleStartupFailure(e);
        }
    }

    /**
     * Spring 上下文关闭时关闭 tailscale。
     *
     * @param event 上下文关闭事件
     */
    @EventListener
    public void onContextClosed(ContextClosedEvent event) {
        closeClient();
    }

    /**
     * Bean 销毁兜底关闭。
     */
    @PreDestroy
    public void destroy() {
        closeClient();
    }

    /**
     * 解析本地端口（处理 {@code server.port=0} 随机端口场景）。
     *
     * @param event 应用就绪事件
     * @return 本地端口
     */
    private int resolveLocalPort(ApplicationReadyEvent event) {
        String portStr = event.getApplicationContext().getEnvironment().getProperty("server.port");
        if (portStr != null && !portStr.isBlank() && !"0".equals(portStr)) {
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                log.debug("[Tailscale] server.port 解析失败: {}", e.getMessage());
            }
        }
        return -1;
    }

    /**
     * 启动隧道。
     *
     * @param localPort 本地端口
     */
    private void startTunnel(int localPort) {
        client = buildClient(localPort);
        client.start();

        log.info("");
        log.info("=========================================================");
        log.info("  Tailscale Funnel 已建立，本机服务已暴露到公网");
        log.info("=========================================================");
        List<String> urls = client.getUrls();
        for (String url : urls) {
            log.info("  {}", url);
            ServiceInfoRegistry.getInstance()
                    .registerEndpoint("公网隧道", "tailscale", url, "Tailscale Funnel 公网隧道");
        }
        log.info("=========================================================");
        log.info("");
    }

    /**
     * 构建 Tailscale Funnel 客户端，注入所有可选项。
     *
     * @param localPort 本地端口
     * @return 客户端实例
     */
    private TailscaleTunnelClient buildClient(int localPort) {
        TailscaleTunnelClient c = StringUtils.hasText(properties.getBinary())
                ? TailscaleTunnelClient.create(resolveBinary(properties.getBinary()))
                : TailscaleTunnelClient.create();
        c.url("http://127.0.0.1:" + localPort);
        String authKey = properties.resolveAuthKey();
        if (StringUtils.hasText(authKey)) {
            c.authKey(authKey.trim());
        }
        if (properties.getStartTimeout() != null) {
            c.startTimeout(properties.getStartTimeout());
        }
        return c;
    }

    /**
     * 解析 binary 配置为路径。
     *
     * @param binary 配置值，可为绝对路径或相对路径
     * @return 解析后的路径
     */
    private static Path resolveBinary(String binary) {
        Path path = Paths.get(binary.trim());
        return path.isAbsolute() ? path : path.toAbsolutePath();
    }

    /**
     * 启动失败处理（根据 failFast 决定行为）。
     *
     * @param e 异常
     */
    private void handleStartupFailure(Exception e) {
        log.error("[Tailscale] 隧道启动失败: {}", e.getMessage(), e);
        closeClient();
        if (properties.isFailFast()) {
            throw new IllegalStateException("[Tailscale] failFast=true，终止 Spring 启动", e);
        }
    }

    /**
     * 关闭 Tailscale Funnel 客户端。
     */
    private void closeClient() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("[Tailscale] 关闭客户端异常: {}", e.getMessage());
            }
            client = null;
        }
    }

    /**
     * Tailscale 服务信息 contributor。
     *
     * @return Tailscale 服务信息 contributor 的结果
     */
    @Bean
    public ServiceInfoContributor tailscaleServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("tailscale", "内网穿透", true, "tailscale 隧道模块");
        };
    }

    /**
     * 内网穿透提供方实现（SPI）。
     *
     * <p>注册为 Bean 后由 {@code spring-support-tunnel-starter} 的
     * {@code TunnelProviderRegistry} 自动收集，统一入口 {@code /ms/tunnel}
     * 即刻可用——本模块不再自带 Controller 与编排 Service。</p>
     *
     * @return 提供方实现
     */
    @Bean
    @ConditionalOnMissingBean
    public TailscaleTunnelProvider tailscaleTunnelProvider() {
        return new TailscaleTunnelProvider(properties);
    }
}
