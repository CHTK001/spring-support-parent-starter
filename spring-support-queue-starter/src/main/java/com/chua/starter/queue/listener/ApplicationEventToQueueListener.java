package com.chua.starter.queue.listener;

import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.annotation.EventToQueue;
import com.chua.starter.queue.properties.QueueProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ApplicationEvent转队列监听器
 * <p>
 * 自动监听所有标注了@EventToQueue的ApplicationEvent，并将其发送到指定队列
 * </p>
 *
 * @author CH
 * @since 2025-03-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationEventToQueueListener {

    private final List<MessageTemplate> messageTemplates;
    private final QueueProperties queueProperties;
    private final Map<String, MessageTemplate> templateCache = new ConcurrentHashMap<>();

    /**
     * 监听所有ApplicationEvent
     */
    @Async
    @EventListener
    public void handleApplicationEvent(ApplicationEvent event) {
        Class<?> eventClass = event.getClass();
        EventToQueue annotation = eventClass.getAnnotation(EventToQueue.class);

        if (annotation == null) {
            return;
        }

        try {
            String destination = annotation.value();
            String type = annotation.type().isEmpty() ? queueProperties.getType() : annotation.type();
            boolean async = annotation.async();

            MessageTemplate template = getMessageTemplate(type);
            if (template == null) {
                log.warn("No MessageTemplate found for type: {}, skipping event: {}",
                    type, eventClass.getSimpleName());
                return;
            }

            Map<String, Object> headers = parseHeaders(annotation.headers());
            headers.put("eventType", eventClass.getName());
            headers.put("eventTime", System.currentTimeMillis());

            if (async) {
                template.sendAsync(destination, event, headers);
                log.debug("Event sent asynchronously to queue: {} -> {}",
                    eventClass.getSimpleName(), destination);
            } else {
                template.send(destination, event, headers);
                log.debug("Event sent to queue: {} -> {}",
                    eventClass.getSimpleName(), destination);
            }
        } catch (Exception e) {
            log.error("Failed to send event to queue: {}", eventClass.getSimpleName(), e);
        }
    }

    /**
     * 获取MessageTemplate
     */
    private MessageTemplate getMessageTemplate(String type) {
        return templateCache.computeIfAbsent(type, t -> {
            if (messageTemplates == null) {
                return null;
            }
            return messageTemplates.stream()
                .filter(template -> template.getType().equals(t))
                .findFirst()
                .orElse(null);
        });
    }

    /**
     * 解析消息头
     */
    private Map<String, Object> parseHeaders(String[] headerArray) {
        Map<String, Object> headers = new HashMap<>();
        if (headerArray == null || headerArray.length == 0) {
            return headers;
        }

        for (String header : headerArray) {
            String[] parts = header.split("=", 2);
            if (parts.length == 2) {
                headers.put(parts[0].trim(), parts[1].trim());
            }
        }
        return headers;
    }
}
