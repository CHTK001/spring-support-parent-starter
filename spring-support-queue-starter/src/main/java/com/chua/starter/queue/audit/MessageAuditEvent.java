package com.chua.starter.queue.audit;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 消息审计事件。
 * <p>
 * 由 {@link MessageAuditInterceptor} 在消息发送/接收完成后发布，
 * 业务方可通过 {@code @EventListener} 监听并持久化到数据库或其他存储。
 * </p>
 *
 * @since 2026-08-15
 * @author CH
 */
@Getter
public class MessageAuditEvent extends ApplicationEvent {

    /**
     * 审计日志数据
     */
    private final MessageAuditLog auditLog;

    /**
     * 构造消息审计事件。
     *
     * @param source    事件源（一般为拦截器实例）
     * @param auditLog  审计日志
     */
    public MessageAuditEvent(Object source, MessageAuditLog auditLog) {
        /**
         * 父。
         * @param source 源
         */
        super(source);
        this.auditLog = auditLog;
    }
}
