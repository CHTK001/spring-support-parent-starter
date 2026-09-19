package com.chua.starter.queue.audit;

import com.chua.starter.queue.Message;
import com.chua.starter.queue.interceptor.MessageInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息审计拦截器
 * <p>
 * 自动记录消息的审计日志
 * </p>
 *
 * @since 2025-03-20
 * @author CH
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "plugin.queue.audit", name = "enabled", havingValue = "true")
public class MessageAuditInterceptor implements MessageInterceptor, ApplicationEventPublisherAware {

    /**
     * 事件publisher
    */
    private ApplicationEventPublisher eventPublisher;

    private final ConcurrentHashMap<String, MessageAuditLog> auditLogs = new ConcurrentHashMap<>(); // 审计日志
    private final ConcurrentHashMap<String, Long> startTimes = new ConcurrentHashMap<>(); // 启动时间

    @Override
    /**
     * 设置application事件发布
    */
    public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    /**
     * 之前发送
    */
    public Message beforeSend(Message message) {
        startTimes.put(message.getId(), System.currentTimeMillis());

        MessageAuditLog auditLog = createAuditLog(message, "SEND");
        auditLog.setOperationTime(LocalDateTime.now());
        auditLog.setStatus("PROCESSING");

        auditLogs.put(message.getId(), auditLog);

        return message;
    }

    @Override
    /**
     * 之后发送
    */
    public void afterSend(Message message, boolean success, Throwable error) {
        MessageAuditLog auditLog = auditLogs.get(message.getId());
        if (auditLog != null) {
            Long startTime = startTimes.remove(message.getId());
            if (startTime != null) {
                auditLog.setDuration(System.currentTimeMillis() - startTime);
            }

            auditLog.setStatus(success ? "SUCCESS" : "FAILURE");
            if (error != null) {
                auditLog.setErrorMessage(error.getMessage());
                auditLog.setErrorStack(getStackTrace(error));
            }

            // 持久化审计日志（发布事件，由监听方写入数据库或其他存储）
            /**
             * 发布审计日志。
             * @param auditLog 审计日志
             */
            publishAuditLog(auditLog);

            // 无论大小都清理对应记录，避免内存泄漏
            auditLogs.remove(message.getId());
        }
    }

    @Override
    /**
     * 之前接收
    */
    public Message beforeReceive(Message message) {
        startTimes.put(message.getId() + "_receive", System.currentTimeMillis());

        MessageAuditLog auditLog = createAuditLog(message, "RECEIVE");
        auditLog.setOperationTime(LocalDateTime.now());
        auditLog.setStatus("PROCESSING");

        auditLogs.put(message.getId() + "_receive", auditLog);

        return message;
    }

    @Override
    /**
     * 之后接收
    */
    public void afterReceive(Message message, boolean success, Throwable error) {
        MessageAuditLog auditLog = auditLogs.get(message.getId() + "_receive");
        if (auditLog != null) {
            Long startTime = startTimes.remove(message.getId() + "_receive");
            if (startTime != null) {
                auditLog.setDuration(System.currentTimeMillis() - startTime);
            }

            auditLog.setStatus(success ? "SUCCESS" : "FAILURE");
            if (error != null) {
                auditLog.setErrorMessage(error.getMessage());
                auditLog.setErrorStack(getStackTrace(error));
            }

            // 持久化审计日志（发布事件，由监听方写入数据库或其他存储）
            /**
             * 发布审计日志。
             * @param auditLog 审计日志
             */
            publishAuditLog(auditLog);

            // 无论大小都清理对应记录，避免内存泄漏
            auditLogs.remove(message.getId() + "_receive");
        }
    }

    /**
     * 创建审计日志
     * @param message 消息
     * @param operation operation
     * @return 创建审计日志的结果
     */
    private MessageAuditLog createAuditLog(Message message, String operation) {
        MessageAuditLog auditLog = new MessageAuditLog();
        auditLog.setId(UUID.randomUUID().toString());
        auditLog.setMessageId(message.getId());
        auditLog.setDestination(message.getDestination());
        auditLog.setMessageType(message.getType());
        auditLog.setOperation(operation);

 // 提取租户标识和用户标识
        Object tenantId = message.getHeader("tenantId");
        if (tenantId instanceof Long) {
            auditLog.setTenantId((Long) tenantId);
        }

        Object userId = message.getHeader("userId");
        if (userId instanceof Long) {
            auditLog.setUserId((Long) userId);
        }

        // 消息大小
        if (message.getPayload() != null) {
            auditLog.setMessageSize(message.getPayload().length);
        }

        // 消息内容摘要
        String payloadStr = message.getPayloadAsString();
        if (payloadStr != null && payloadStr.length() > 100) {
            auditLog.setPayloadSummary(payloadStr.substring(0, 100) + "...");
        } else {
            auditLog.setPayloadSummary(payloadStr);
        }

        // 消息头
        auditLog.setHeaders(message.getHeaders());

        return auditLog;
    }

    /**
     * 发布审计日志事件，由外部监听器持久化
     * @param auditLog 审计日志
     */
    private void publishAuditLog(MessageAuditLog auditLog) {
        log.info("[Audit] 消息审计: operation={}, destination={}, status={}, duration={}ms, messageId={}",
            auditLog.getOperation(), auditLog.getDestination(), auditLog.getStatus(),
            auditLog.getDuration(), auditLog.getMessageId());

        // 发布 Spring 事件，供监听器持久化到数据库或其它存储
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new MessageAuditEvent(this, auditLog));
        }
    }

    /**
     * 获取异常堆栈
     * @param error 错误
     * @return 获取stack追踪的结果
     */
    private String getStackTrace(Throwable error) {
        if (error == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(error.toString()).append("\n");
        for (StackTraceElement element : error.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
            if (sb.length() > 2000) {
                sb.append("\t...");
                break;
            }
        }
        return sb.toString();
    }

    /**
     * 获取审计日志
     * @param messageId 消息标识
     * @return 获取审计日志的结果
     */
    public MessageAuditLog getAuditLog(String messageId) {
        return auditLogs.get(messageId);
    }

    /**
     * 清理审计日志
     */
    public void clearAuditLogs() {
        auditLogs.clear();
        startTimes.clear();
    }

    @Override
    /**
     * 获取订单
    */
    public int getOrder() {
        return -500; // 高优先级
    }
}
