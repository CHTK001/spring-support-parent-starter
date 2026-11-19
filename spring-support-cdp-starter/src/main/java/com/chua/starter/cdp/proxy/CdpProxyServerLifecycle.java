package com.chua.starter.cdp.proxy;

import com.chua.common.support.network.server.Server;
import com.chua.starter.cdp.config.CdpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * 实时页面代理服务器生命周期
 *
 * <p>应用就绪后在<b>独立端口</b>（{@code spring.cdp.proxy.port}，默认 9223）启动一个
 * chua NIO {@link Server}，挂载 {@link CdpDebugProxyFilter}；应用关闭时停止。
 * 独立端口使页面内的根相对地址（{@code /x}）天然落在代理路径空间内，避免与主服务
 * （8082 / Spring MVC）的路径前缀互相干扰。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CdpProxyServerLifecycle {

    /**
     * CDP 属性
     */
    private final CdpProperties properties;

    /**
     * 会话管理器
     */
    private final ProxySessionManager sessionManager;

    /**
     * 脚本注入器
     */
    private final CdpProxyScriptInjector scriptInjector;

    /**
     * 代理服务器实例
     */
    private Server server;

    /**
     * 应用就绪后启动代理
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        CdpProperties.LiveProxy config = properties.getLiveProxy();
        if (!config.isEnabled()) {
            log.info("[cdp-proxy] 实时页面代理未启用（spring.cdp.proxy.enabled=false）");
            return;
        }
        try {
            server = Server.builder()
                    .type("nio")
                    .port(config.getPort())
                    .host(config.getHost())
                    .build()
                    .addFilter(new CdpDebugProxyFilter(properties, sessionManager, scriptInjector));
            server.start();
            log.info("[cdp-proxy] 实时页面代理已启动: http://{}:{}（会话 TTL {}s，最大 {} 个）",
                    config.getHost(), config.getPort(),
                    config.getSessionTtl().toSeconds(), config.getMaxSessions());
        } catch (Exception e) {
            log.error("[cdp-proxy] 实时页面代理启动失败（端口 {} 可能被占用）: {}",
                    config.getPort(), e.getMessage(), e);
            server = null;
        }
    }

    /**
     * 应用关闭前停止代理
     */
    @PreDestroy
    public void stop() {
        if (server == null) {
            return;
        }
        try {
            server.stop();
            log.info("[cdp-proxy] 实时页面代理已停止");
        } catch (Exception e) {
            log.debug("[cdp-proxy] 停止代理失败: {}", e.getMessage());
        } finally {
            server = null;
        }
    }
}
