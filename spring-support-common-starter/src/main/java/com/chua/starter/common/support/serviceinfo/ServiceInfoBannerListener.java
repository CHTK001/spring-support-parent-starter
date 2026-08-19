package com.chua.starter.common.support.serviceinfo;

import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;

/**
 * 启动横幅监听器。
 *
 * <p>在 {@link ApplicationReadyEvent} 时输出横幅;
 * 同时响应 {@link ApplicationFailedEvent}, 在启动失败时输出"已注册但未展示"的开关状态,
 * 帮助排错。</p>
 *
 * <p>实现 {@link SmartApplicationListener} 以支持多事件类型监听。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class ServiceInfoBannerListener implements SmartApplicationListener {

    /** Detector */
    private final ServiceInfoEndpointDetector detector;
    /** 属性 */
    private final ServiceInfoProperties properties;
    private volatile boolean bannerPrinted = false;

    public ServiceInfoBannerListener(ServiceInfoEndpointDetector detector, ServiceInfoProperties properties) {
        this.detector = detector;
        this.properties = properties;
    }

    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return ApplicationReadyEvent.class.isAssignableFrom(eventType)
                || ApplicationFailedEvent.class.isAssignableFrom(eventType);
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationReadyEvent) {
            onReady((ApplicationReadyEvent) event);
        } else if (event instanceof ApplicationFailedEvent) {
            onFailed((ApplicationFailedEvent) event);
        }
    }

    private void onReady(ApplicationReadyEvent event) {
        if (!properties.isBanner()) {
            return;
        }
        try {
            detector.detectAndRegister();
            ServiceInfoAutoConfiguration.printBanner(ServiceInfoRegistry.getInstance());
            bannerPrinted = true;
        } catch (Exception e) {
            System.err.println("[ServiceInfo] 横幅渲染失败: " + e.getMessage());
        }
    }

    private void onFailed(ApplicationFailedEvent event) {
        if (bannerPrinted) {
            return;
        }
        try {
            ServiceInfoRegistry registry = ServiceInfoRegistry.getInstance();
            if (registry.getFlags().isEmpty() && registry.getEndpoints().isEmpty()) {
                return;
            }
            System.err.println("[ServiceInfo] 应用启动失败, 已注册的开关/端点如下, 用于排错:");
            ServiceInfoAutoConfiguration.printBanner(registry);
        } catch (Exception e) {
            // 启动失败时静默, 不抛出
        }
    }

    // SmartApplicationListener requires these methods
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}