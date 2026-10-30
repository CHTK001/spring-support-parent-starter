package com.chua.starter.queue.router;

import com.chua.starter.queue.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于租户的消息路由器
 * <p>
 * 根据消息中的租户标识路由到不同的队列
 * </p>
 *
 * @since 2025-03-20
 * @author CH
 */
@Slf4j
@Component
public class TenantBasedMessageRouter implements MessageRouter {

    @Override
    /**
     * Route
     * @param message 消息
     * @return 返回值
    */
    public String route(Message message) {
        Object tenantId = message.getHeader("tenantId");
        if (tenantId == null) {
            return null; // 使用原始目标
        }

        String originalDestination = message.getDestination();
        String routedDestination = originalDestination + ".tenant." + tenantId;

        log.debug("[Queue] 租户路由: {} -> {}", originalDestination, routedDestination);
        return routedDestination;
    }

    @Override
    /**
     * 支持
     * @param message 消息
     * @return 返回值
    */
    public boolean supports(Message message) {
        return message.getHeader("tenantId") != null;
    }

    @Override
    /**
     * 获取订单
     * @return 返回值
    */
    public int getOrder() {
        return -100;
    }
}
