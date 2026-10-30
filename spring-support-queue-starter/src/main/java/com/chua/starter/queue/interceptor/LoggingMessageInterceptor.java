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
 * @since 2025-03-20
 * @author CH
 */
@Slf4j
@Component
public class LoggingMessageInterceptor implements MessageInterceptor {

    @Override
    /**
     * 之前发送
     * @param message 消息
     * @return 返回值
    */
    public Message beforeSend(Message message) {
        log.debug("[Queue] 准备发送消息: destination={}, id={}, size={}",
            message.getDestination(), message.getId(),
            message.getPayload() != null ? message.getPayload().length : 0);
        return message;
    }

    @Override
    /**
     * 之后发送
     * @param error 可抛出异常
     * @param message 消息
     * @param success 标记成功
     * @return 返回值
    */
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
    /**
     * 之前接收
     * @param message 消息
     * @return 返回值
    */
    public Message beforeReceive(Message message) {
        log.debug("[Queue] 接收到消息: destination={}, id={}, size={}",
            message.getDestination(), message.getId(),
            message.getPayload() != null ? message.getPayload().length : 0);
        return message;
    }

    @Override
    /**
     * 之后接收
     * @param error 可抛出异常
     * @param message 消息
     * @param success 标记成功
     * @return 返回值
    */
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
    /**
     * 获取订单
     * @return 返回值
    */
    public int getOrder() {
        return Integer.MAX_VALUE; // 最低优先级，最后执行
    }
}
