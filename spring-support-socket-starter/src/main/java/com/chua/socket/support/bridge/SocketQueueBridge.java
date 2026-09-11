package com.chua.socket.support.bridge;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.queue.Message;
import com.chua.starter.queue.annotation.QueueListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Queue → Socket 桥接监听器
 * <p>
 * 监听消息队列中约定前缀（{@code socket:}）的 topic，将消息内容解析后
 * 通过 {@link SocketSessionTemplate} 广播到前端。业务模块（如监控）无需
 * 直接依赖 Socket，只向 queue 发送 {@code socket:<module>:<event>} 消息即可。
 * </p>
 *
 * <h3>协议</h3>
 * <pre>
 * topic:   socket:monitor:alarm        （socket:{module}:{event}）
 * payload: { "module":"monitor", "event":"alarm", "data":{...}, "dataId": "...", "timestamp": 123 }
 * 广播格式对齐前端 WsMessage（socket.io "message" 事件），前端 socket.subscribe(module, event) 按 module_event 分发。
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocketQueueBridge {

    /** topic 前缀：socket:{module}:{event} */
    private static final String TOPIC_PREFIX = "socket:";

    /** Socket 会话模板（socket 未启用时为空实现） */
    private final ObjectProvider<SocketSessionTemplate> socketTemplateProvider;

    /** JSON 解析器 */
    private final ObjectMapper objectMapper;

    /**
     * 监听 socket:* 前缀 topic，收到后广播到所有前端会话。
     * 依赖 queue-starter 的 {@link QueueListener} 注册（plugin.queue.enable=true 时生效）。
     *
     * @param message 队列消息（payload 为 JSON 字符串）
     */
    @QueueListener("socket:*")
    public void onSocketMessage(Message message) {
        SocketSessionTemplate template = socketTemplateProvider.getIfAvailable();
        if (template == null) {
            log.debug("[socket-bridge] Socket 未启用，跳过广播 topic={}", message.getDestination());
            return;
        }
        try {
            String topic = message.getDestination();
            String rest = topic.startsWith(TOPIC_PREFIX) ? topic.substring(TOPIC_PREFIX.length()) : topic;
            int sep = rest.indexOf(':');
            String module = sep > 0 ? rest.substring(0, sep) : rest;
            String event = sep > 0 ? rest.substring(sep + 1) : "message";

            Object payload = new String(message.getPayload(), java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> dataMap = parsePayload(payload);
            if (dataMap == null) {
                dataMap = new LinkedHashMap<>(4);
                dataMap.put("module", module);
                dataMap.put("event", event);
                dataMap.put("data", payload);
            }
            // 若 payload 内带 event/模块 覆盖 topic 推导的默认值
            Object innerEvent = dataMap.get("event");
            if (innerEvent != null) {
                event = String.valueOf(innerEvent);
            }
            Object innerModule = dataMap.get("module");
            if (innerModule != null) {
                module = String.valueOf(innerModule);
            }
            dataMap.putIfAbsent("module", module);
            dataMap.putIfAbsent("event", event);
            dataMap.putIfAbsent("timestamp", System.currentTimeMillis());
            template.broadcast("message", objectMapper.writeValueAsString(dataMap));
            log.debug("[socket-bridge] 广播 topic={} module={} event={}", topic, module, event);
        } catch (Exception e) {
            log.warn("[socket-bridge] 广播失败 topic={}: {}", message.getDestination(), e.getMessage());
        }
    }

    /**
     * 解析消息载荷为 Map（兼容 JSON 对象）
     *
     * @param payload 载荷字符串
     * @return Map 或 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(Object payload) {
        try {
            Object parsed = objectMapper.readValue((String) payload, Object.class);
            return parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
        } catch (Exception e) {
            return null;
        }
    }
}