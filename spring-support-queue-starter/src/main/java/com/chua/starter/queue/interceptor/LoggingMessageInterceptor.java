package com.chua.starter.queue.interceptor;

import com.chua.starter.queue.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 日志拦截器
 * <p>
 * 记录消息发送和接收的日志
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
@Component
public class LoggingMessageInterceptor implements MessageInterceptor {

    @Override
    public Message beforeSend(Message message) {
        log.debug("[Queue] 准备发送消息: destination={}, id={}, size={}",
            message.getDestination(), message.getId(),
            message.getPayload() != null ? message.getPayload().length : 0);
        return message;
    }

    @Override
    public void afterSend(Message message, boolean success, Throwable error) {
        if (success) {
            log.debug("[Queue] 消息发送成功: destination={}, id={}",
                message.getDestination(), message.getId());
        } else {
            log.error("[Queue] 消息发送失败: destination={}, id={}",
                message.getDestination(), message.getId(), error);
        }
    }

    @Override
    public Message beforeReceive(Message message) {
        log.debug("[Queue] 接收到消息: destination={}, id={}, size={}",
            message.getDestination(), message.getId(),
            message.getPayload() != null ? message.getPayload().length : 0);
        return message;
    }

    @Override
    public void afterReceive(Message message, boolean success, Throwable error) {
        if (success) {
            log.debug("[Queue] 消息处理成功: destination={}, id={}",
                message.getDestination(), message.getId());
        } else {
            log.error("[Queue] 消息处理失败: destination={}, id={}",
                message.getDestination(), message.getId(), error);
        }
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE; // 最低优先级，最后执行
    }
}
