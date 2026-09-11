package com.chua.socket.support.bridge;

import com.chua.socket.support.MsgEvent;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.queue.Message;
import com.chua.starter.queue.annotation.QueueListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Queue → Socket 桥接监听器
 * <p>
 * 监听消息队列中约定前缀（{@code socket:}）的 topic，将消息体解析后
 * 通过 {@link SocketSessionTemplate} 广播到前端。业务侧（如监控模块）
 * 无需直接依赖 Socket，只向 queue 发送 {@code socket:<module>:<event>} 消息即可。
 * </p>
 *
 * <h3>协议</h3>
 * <pre>
 * topic:  socket:monitor:alarm   （socket:{module}:{event}）
 * payload(JSON): {
 *   event: "alarm",            // 广播事件名（前端 subscribe(module, event) 匹配）
 *   data:  {...}               // 事件数据
 * }
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
     * 解析失败或 socket 未启用时静默跳过，不影响 queue 消费。
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
            // topic: socket:monitor:alarm -> module=monitor, event=alarm
            String rest = topic.startsWith(TOPIC_PREFIX) ? topic.substring(TOPIC_PREFIX.length()) : topic;
            int sep = rest.indexOf(':');
            String module = sep > 0 ? rest.substring(0, sep) : rest;
            String event = sep > 0 ? rest.substring(sep + 1) : "message";

            Object payload = new String(message.getPayload(), java.nio.charset.StandardCharsets.UTF_8);
            Object data = payload;
            try {
                data = objectMapper.readValue((String) payload, Object.class);
            } catch (Exception ignored) {
                // 非 JSON 载荷原样透传
            }
            // 若 payload 内带 event/data 覆盖约定字段
            if (data instanceof Map<?, ?> map) {
                Object innerEvent = map.get("event");
                if (innerEvent != null) {
                    event = String.valueOf(innerEvent);
                }
                if (map.containsKey("data")) {
                    data = map.get("data");
                }
            }
            MsgEvent msg = MsgEvent.builder()
                    .module(module)
                    .name(event)
                    .data(data instanceof java.io.Serializable s ? s : String.valueOf(data))
                    .timestamp(System.currentTimeMillis())
                    .build();
            template.broadcast(module + ":" + event, msg);
            log.debug("[socket-bridge] 广播 topic={} module={} event={}", topic, module, event);
        } catch (Exception e) {
            log.warn("[socket-bridge] 广播失败 topic={}: {}", message.getDestination(), e.getMessage());
        }
    }
}
