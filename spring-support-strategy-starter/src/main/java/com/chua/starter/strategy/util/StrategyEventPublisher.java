package com.chua.starter.strategy.util;

import com.chua.starter.strategy.event.StrategyEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

/**
 * 策略事件发布工具类
 * <p>
 * 统一管理策略事件的发布，如果ApplicationEventPublisher不存在则静默处理。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
public class StrategyEventPublisher {

    @Nullable
    private static ApplicationEventPublisher eventPublisher;

    /**
     * 设置事件发布器（由Spring容器注入）
     *
     * @param publisher 事件发布器
     */
    public static void setEventPublisher(@Nullable ApplicationEventPublisher publisher) {
        eventPublisher = publisher;
    }

    /**
     * 发布策略事件
     *
     * @param event 策略事件
     */
    public static void publishEvent(StrategyEvent event) {
        if (eventPublisher == null) {
            log.debug("ApplicationEventPublisher未设置，跳过事件发布: {}", event.getEventType());
            return;
        }

        try {
            eventPublisher.publishEvent(event);
            log.debug("[策略模块][事件发布]发布事件: type={}, uri={}, allowed={}",
                    event.getEventType(), event.getRequestUri(), event.isAllowed());
        } catch (Exception e) {
            log.warn("[策略模块][事件发布]发布事件失败: type={}", event.getEventType(), e);
        }
    }
}


