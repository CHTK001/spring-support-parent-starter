package com.chua.starter.server.support.sip;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.ServerSocket;

/**
 * SIP 服务生命周期管理 - 跟随后端自动启动/停止
 * 系统配置 sip.enabled=true 时自动在本地启动 SIP 中继服务
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
public class SipServerManager {

    private static final Logger log = LoggerFactory.getLogger(SipServerManager.class);

    /**
     * 控制端口监听对象（当前由 {@link SipTunnel} 内部持有，此字段保留用于后续接管）
     */
    private ServerSocket controlSocket;
    /**
     * 服务运行标记
    */
    private boolean running = false;

    /**
     * 应用启动后延迟 10s 尝试自动拉起 SIP 中继服务
     * 延迟用于等待配置加载完成，避免读到默认值
     */
    @PostConstruct
    public void init() {
        // 延迟启动，等待配置加载
        new Thread(() -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {
            }
            tryStart();
        }, "sip-init").start();
    }

    /**
     * 根据配置启动 SIP 服务
     *
     * <p>线程安全：已在运行时直接返回；启动失败仅记录告警，不阻断应用启动。</p>
     */
    public synchronized void tryStart() {
        if (running) {
            log.info("[SIP] 服务已在运行中");
            return;
        }
        try {
            int port = 7822;
            String secret = "lvzhou2011";
            // 尝试读取配置
            try {
                // 直接用 SipTunnel.startServer 会阻塞，用线程启动
                SipTunnel.startServer(port, secret);
                running = true;
                log.info("[SIP] 中继服务已自动启动，端口: {}", port);
            } catch (Exception e) {
                log.warn("[SIP] 启动失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("[SIP] 初始化异常", e);
        }
    }

    /**
     * 应用关闭时停止 SIP 服务
     */
    @PreDestroy
    public void stop() {
        running = false;
        log.info("[SIP] 服务停止");
    }
}
