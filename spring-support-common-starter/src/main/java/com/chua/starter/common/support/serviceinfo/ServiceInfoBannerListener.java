package com.chua.starter.common.support.serviceinfo;

import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(ServiceInfoBannerListener.class); // 日志

    /**
     * Detector
    */
    private final ServiceInfoEndpointDetector detector;
    /**
     * 属性
    */
    private final ServiceInfoProperties properties;
    private volatile boolean bannerPrinted = false; // bannerprinted

    /**
     * 创建 服务信息banner监听器 实例
     * @param detector detector
     * @param properties 服务信息属性
     * @param properties 属性
     */
    public ServiceInfoBannerListener(ServiceInfoEndpointDetector detector, ServiceInfoProperties properties) {
        this.detector = detector;
        this.properties = properties;
    }

    @Override
    /**
     * 支持事件类型
     * @param eventType 参数
     * @return 返回值
    */
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return ApplicationReadyEvent.class.isAssignableFrom(eventType)
                || ApplicationFailedEvent.class.isAssignableFrom(eventType);
    }

    @Override
    /**
     * onapplication事件
     * @param event 参数
     * @return 返回值
    */
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationReadyEvent) {
            /**
             * on就绪。
             * @param (ApplicationReadyEvent (application就绪事件
             */
            onReady((ApplicationReadyEvent) event);
        } else if (event instanceof ApplicationFailedEvent) {
            /**
             * on失败。
             * @param (ApplicationFailedEvent (application失败事件
             */
            onFailed((ApplicationFailedEvent) event);
        }
    }

    /**
     * on就绪
     *
     * @param event 事件
     */
    private void onReady(ApplicationReadyEvent event) {
        if (!properties.isBanner()) {
            return;
        }
        try {
            detector.detectAndRegister();
            ServiceInfoAutoConfiguration.printBanner(ServiceInfoRegistry.getInstance());
            bannerPrinted = true;
        } catch (Exception e) {
            log.error("[ServiceInfo] 横幅渲染失败", e);
        }
    }

    /**
     * on失败
     *
     * @param event 事件
     */
    private void onFailed(ApplicationFailedEvent event) {
        if (bannerPrinted) {
            return;
        }
        try {
            ServiceInfoRegistry registry = ServiceInfoRegistry.getInstance();
            if (registry.getFlags().isEmpty() && registry.getEndpoints().isEmpty()) {
                return;
            }
            log.warn("[ServiceInfo] 应用启动失败, 已注册的开关/端点如下, 用于排错:");
            ServiceInfoAutoConfiguration.printBanner(registry);
        } catch (Exception e) {
            // 启动失败时静默, 不抛出
        }
    }

 // smartapplication监听器 requires these 方法
    @Override
    /**
     * 获取订单
     * @return 返回值
    */
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
