package com.chua.starter.queue.audit;

import com.chua.starter.queue.Message;
import com.chua.starter.queue.interceptor.MessageInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "plugin.queue.audit", name = "enabled", havingValue = "true")
public class MessageAuditInterceptor implements MessageInterceptor {

    private final ConcurrentHashMap<String, MessageAuditLog> auditLogs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> startTimes = new ConcurrentHashMap<>();

    @Override
    public Message beforeSend(Message message) {
        startTimes.put(message.getId(), System.currentTimeMillis());

        MessageAuditLog auditLog = createAuditLog(message, "SEND");
        auditLog.setOperationTime(LocalDateTime.now());
        auditLog.setStatus("PROCESSING");

        auditLogs.put(message.getId(), auditLog);

        return message;
    }

    @Override
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

            // 持久化审计日志（这里只是记录到内存，实际应该写入数据库）
            logAudit(auditLog);

            // 清理（可选）
            if (auditLogs.size() > 10000) {
                auditLogs.remove(message.getId());
            }
        }
    }

    @Override
    public Message beforeReceive(Message message) {
        startTimes.put(message.getId() + "_receive", System.currentTimeMillis());

        MessageAuditLog auditLog = createAuditLog(message, "RECEIVE");
        auditLog.setOperationTime(LocalDateTime.now());
        auditLog.setStatus("PROCESSING");

        auditLogs.put(message.getId() + "_receive", auditLog);

        return message;
    }

    @Override
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

            // 持久化审计日志
            logAudit(auditLog);

            // 清理
            if (auditLogs.size() > 10000) {
                auditLogs.remove(message.getId() + "_receive");
            }
        }
    }

    /**
     * 创建审计日志
     */
    private MessageAuditLog createAuditLog(Message message, String operation) {
        MessageAuditLog auditLog = new MessageAuditLog();
        auditLog.setId(UUID.randomUUID().toString());
        auditLog.setMessageId(message.getId());
        auditLog.setDestination(message.getDestination());
        auditLog.setMessageType(message.getType());
        auditLog.setOperation(operation);

        // 提取租户ID和用户ID
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
     * 记录审计日志
     */
    private void logAudit(MessageAuditLog auditLog) {
        log.info("[Audit] 消息审计: operation={}, destination={}, status={}, duration={}ms, messageId={}",
            auditLog.getOperation(), auditLog.getDestination(), auditLog.getStatus(),
            auditLog.getDuration(), auditLog.getMessageId());

        // TODO: 持久化到数据库
        // auditLogRepository.save(auditLog);
    }

    /**
     * 获取异常堆栈
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
    public int getOrder() {
        return -500; // 高优先级
    }
}
