package com.chua.socket.support.bridge;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.queue.Message;
import com.chua.starter.queue.annotation.QueueListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
* 队列 → 套接字 桥接监听器
* <p>
* 监听固定 topic {@code socket:push}（业务方统一向此 topic 发送），
* 从消息 payload 解析 {@code {module, event, data, dataId}} 后，
* 通过 {@link SocketSessionTemplate} 广播到前端。
* 业务模块（如监控）无需直接依赖 套接字，只向 队列 发消息即可。
* </p>
*
* <h3>协议</h3>
* <pre>
* topic:   socket:push
* payload: { "module":"monitor", "event":"alarm", "data":{...}, "dataId": "..." }
* 广播格式对齐前端 WsMessage（socket.io "message" 事件，载荷含 module/event/data），
* 前端 socket.subscribe(module, event) 按 module_event 分发。
* </pre>
*
* @author CH
 * @since 4.0.0.42
  */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.queue", name = "enable", havingValue = "true")
@ConditionalOnMissingBean(name = "monitorSocketQueueBridge")
public class SocketQueueBridge {

    /** 固定订阅 topic：业务方统一向此 topic 发送 套接字 广播消息 */
    public static final String TOPIC = "socket:push";

    /** 套接字 会话模板（套接字 未启用时为空实现） */
    private final ObjectProvider<SocketSessionTemplate> socketTemplateProvider;

    /** JSON 解析器 */
    private final ObjectMapper objectMapper;

    /**
    * 监听 套接字:push topic，解析 payload 后广播到所有前端会话
    *
    * @param message 队列消息（payload 为 JSON 字符串）
     */
    @QueueListener(TOPIC)
    public void onSocketMessage(Message message) {
        SocketSessionTemplate template = socketTemplateProvider.getIfAvailable();
        if (template == null) {
            log.debug("[socket-bridge] Socket 未启用，跳过广播 topic={}", message.getDestination());
            return;
        }
        try {
            Map<String, Object> dataMap = parsePayload(message.getPayloadAsString());
            if (dataMap == null) {
                dataMap = new LinkedHashMap<>(3);
                dataMap.put("module", "system");
                dataMap.put("event", "message");
                dataMap.put("data", message.getPayloadAsString());
            }
            dataMap.putIfAbsent("timestamp", System.currentTimeMillis());
            String module = String.valueOf(dataMap.getOrDefault("module", "system"));
            String event = String.valueOf(dataMap.getOrDefault("event", "message"));
            template.broadcast("message", objectMapper.writeValueAsString(dataMap));
            log.debug("[socket-bridge] 广播 module={} event={}", module, event);
        } catch (Exception e) {
            log.warn("[socket-bridge] 广播失败 topic={}: {}", message.getDestination(), e.getMessage());
        }
    }

    /**
    * 解析消息载荷为 映射（兼容 JSON 对象）
    *
    * @param payload 载荷字符串
    * @return Map 或 空（载荷为空或非法 JSON 时）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            Object parsed = objectMapper.readValue(payload, Object.class);
            return parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
        } catch (Exception e) {
            log.debug("[socket-bridge] payload 非 JSON 对象，原样透传: {}", e.getMessage());
            return null;
        }
    }
}