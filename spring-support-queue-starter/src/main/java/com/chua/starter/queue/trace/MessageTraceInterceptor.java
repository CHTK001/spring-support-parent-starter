package com.chua.starter.queue.trace;

import com.chua.starter.queue.Message;
import com.chua.starter.queue.interceptor.MessageInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息链路追踪拦截器
 * <p>
 * 自动记录消息的链路追踪信息
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
@Component
public class MessageTraceInterceptor implements MessageInterceptor {

    private final ConcurrentHashMap<String, MessageTraceContext> traces = new ConcurrentHashMap<>();

    @Override
    public Message beforeSend(Message message) {
        String traceId = generateTraceId();
        String spanId = generateSpanId();

        message.getHeaders().put("X-Trace-Id", traceId);
        message.getHeaders().put("X-Span-Id", spanId);

        MessageTraceContext context = new MessageTraceContext();
        context.setTraceId(traceId);
        context.setSpanId(spanId);
        context.setMessageId(message.getId());
        context.setDestination(message.getDestination());
        context.setSendTime(LocalDateTime.now());
        context.setStatus("SENDING");

        traces.put(message.getId(), context);

        log.debug("[Trace] 消息发送追踪: traceId={}, spanId={}, destination={}",
            traceId, spanId, message.getDestination());

        return message;
    }

    @Override
    public void afterSend(Message message, boolean success, Throwable error) {
        MessageTraceContext context = traces.get(message.getId());
        if (context != null) {
            context.setStatus(success ? "SENT" : "SEND_FAILED");
            if (error != null) {
                context.setErrorMessage(error.getMessage());
            }
        }
    }

    @Override
    public Message beforeReceive(Message message) {
        String traceId = message.getHeaderAsString("X-Trace-Id");
        String parentSpanId = message.getHeaderAsString("X-Span-Id");
        String spanId = generateSpanId();

        message.getHeaders().put("X-Span-Id", spanId);

        MessageTraceContext context = traces.computeIfAbsent(message.getId(), k -> new MessageTraceContext());
        context.setTraceId(traceId);
        context.setSpanId(spanId);
        context.setParentSpanId(parentSpanId);
        context.setMessageId(message.getId());
        context.setDestination(message.getDestination());
        context.setReceiveTime(LocalDateTime.now());
        context.setProcessStartTime(LocalDateTime.now());
        context.setStatus("PROCESSING");

        log.debug("[Trace] 消息接收追踪: traceId={}, spanId={}, parentSpanId={}, destination={}",
            traceId, spanId, parentSpanId, message.getDestination());

        return message;
    }

    @Override
    public void afterReceive(Message message, boolean success, Throwable error) {
        MessageTraceContext context = traces.get(message.getId());
        if (context != null) {
            context.setProcessEndTime(LocalDateTime.now());
            context.setStatus(success ? "COMPLETED" : "FAILED");
            if (error != null) {
                context.setErrorMessage(error.getMessage());
            }

            log.info("[Trace] 消息处理完成: traceId={}, spanId={}, duration={}ms, status={}",
                context.getTraceId(), context.getSpanId(),
                context.getProcessDuration(), context.getStatus());

            // 清理追踪信息（可选，根据需要保留一段时间）
            if (traces.size() > 10000) {
                traces.remove(message.getId());
            }
        }
    }

    /**
     * 获取追踪上下文
     */
    public MessageTraceContext getTraceContext(String messageId) {
        return traces.get(messageId);
    }

    /**
     * 清理追踪信息
     */
    public void clearTraces() {
        traces.clear();
    }

    /**
     * 生成追踪ID
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成跨度ID
     */
    private String generateSpanId() {
        return UUID.randomUUID().toString().substring(0, 16);
    }

    @Override
    public int getOrder() {
        return -1000; // 最高优先级
    }
}
