package com.chua.starter.cloudflared.support;

import com.chua.cloudflared.support.CloudflareTunnelClient;
import com.chua.starter.common.support.serviceinfo.ServiceInfoContributor;
import com.chua.starter.common.support.serviceinfo.ServiceInfoRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Cloudflare Tunnel 自动配置：Spring Boot 启动后自动建立 cloudflared 隧道，将本机服务暴露到公网。
 *
 * <p>触发时机：{@link ApplicationReadyEvent}（此时 {@code server.port} 已确定）
 * → 启动 cloudflared 隧道 → 输出公网 HTTPS URL。</p>
 *
 * <p>关闭时机：{@link ContextClosedEvent} → 优雅关闭 cloudflared 进程。</p>
 *
 * <p>支持三种隧道模式：</p>
 * <ul>
 *   <li>Quick Tunnel（默认，免账号）：{@code cloudflared tunnel --url <local>}</li>
 *   <li>远程管理命名隧道：配置 {@code plugin.cloudflared.token}</li>
 *   <li>固定域名：配置 {@code plugin.cloudflared.hostname}</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(CloudflareTunnelClient.class)
@ConditionalOnProperty(prefix = "plugin.cloudflared", name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CloudflaredProperties.class)
public class CloudflaredAutoConfiguration {

    /**
     * Cloudflare Tunnel 客户端实例
     */
    private CloudflareTunnelClient client;

    /**
     * 配置属性
     */
    private final CloudflaredProperties properties;

    /**
     * 创建 cloudflaredauto配置 实例
     *
     * @param properties 配置属性
     */
    public CloudflaredAutoConfiguration(CloudflaredProperties properties) {
        this.properties = properties;
    }

    /**
     * 在应用就绪后建立 Cloudflare 隧道。
     *
     * @param event 应用就绪事件
     */
    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (client != null) {
            return;
        }
        int localPort = resolveLocalPort(event);
        if (localPort <= 0) {
            log.warn("[Cloudflared] 无法解析本地 server.port，跳过隧道建立");
            return;
        }
        try {
            startTunnel(localPort);
        } catch (Exception e) {
            handleStartupFailure(e);
        }
    }

    /**
     * Spring 上下文关闭时关闭 cloudflared。
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
                log.debug("[Cloudflared] server.port 解析失败: {}", e.getMessage());
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
        log.info("  Cloudflare Tunnel 已建立，本机服务已暴露到公网");
        log.info("=========================================================");
        List<String> urls = client.getUrls();
        for (String url : urls) {
            log.info("  🌐 {}", url);
            ServiceInfoRegistry.getInstance()
                    .registerEndpoint("公网隧道", "cloudflared", url, "Cloudflare Tunnel 公网隧道");
        }
        log.info("=========================================================");
        log.info("");
    }

    /**
     * 构建 Cloudflare Tunnel 客户端，注入所有可选项。
     *
     * @param localPort 本地端口
     * @return 客户端实例
     */
    private CloudflareTunnelClient buildClient(int localPort) {
        CloudflareTunnelClient c = StringUtils.hasText(properties.getBinary())
                ? CloudflareTunnelClient.create(resolveBinary(properties.getBinary()))
                : CloudflareTunnelClient.create();
        if (StringUtils.hasText(properties.getToken())) {
            c.token(properties.getToken().trim());
        }
        if (StringUtils.hasText(properties.getHostname())) {
            c.hostname(properties.getHostname().trim());
        }
        String localUrl = StringUtils.hasText(properties.getLocalUrl())
                ? properties.getLocalUrl().trim()
                : "http://127.0.0.1:" + localPort;
        c.url(localUrl);
        c.noAutoUpdate(properties.isNoAutoUpdate());
        if (StringUtils.hasText(properties.getLogLevel())) {
            c.logLevel(properties.getLogLevel());
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
        log.error("[Cloudflared] 隧道启动失败: {}", e.getMessage(), e);
        closeClient();
        if (properties.isFailFast()) {
            throw new IllegalStateException("[Cloudflared] failFast=true，终止 Spring 启动", e);
        }
    }

    /**
     * 关闭 Cloudflare Tunnel 客户端。
     */
    private void closeClient() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("[Cloudflared] 关闭客户端异常: {}", e.getMessage());
            }
            client = null;
        }
    }

    /**
     * cloudflared服务信息contributor
     *
     * @return cloudflared服务信息contributor的结果
     */
    @Bean
    public ServiceInfoContributor cloudflaredServiceInfoContributor() {
        return registry -> {
            registry.registerFlag("cloudflared", "内网穿透", true, "cloudflared隧道模块");
        };
    }
}