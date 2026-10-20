package com.chua.starter.device.support.realtime;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于宿主 Socket 会话模板的设备事件发布器
 *
 * <p>与前端全局 SSE 客户端约定：事件名固定为 {@code message}，数据体为
 * {@code {module:"device", event:"status", data:...}}。前端
 * {@code socketService.subscribe("device", "status", handler)} 即可收到。</p>
 *
 * <p>用 {@link ObjectProvider} 延迟解析会话模板：宿主未启用 socket 时自动退化为空操作，
 * 不会因为缺少 Bean 导致启动失败。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public class SocketDeviceRealtimePublisher implements DeviceRealtimePublisher {

    /**
     * 前端全局 socket 监听的 SSE 事件名
     */
    private static final String TRANSPORT_EVENT = "message";

    /**
     * JSON 序列化
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 会话模板提供者（延迟解析）
     */
    private final ObjectProvider<SocketSessionTemplate> templateProvider;

    /**
     * 创建发布器
     *
     * @param templateProvider 会话模板提供者
     */
    public SocketDeviceRealtimePublisher(ObjectProvider<SocketSessionTemplate> templateProvider) {
        this.templateProvider = templateProvider;
    }

    @Override
    public void publish(String event, Object payload) {
        SocketSessionTemplate template = templateProvider.getIfAvailable();
        if (template == null) {
            log.debug("[Device][Realtime] 宿主未启用 socket，跳过事件: {}", event);
            return;
        }
        try {
            Map<String, Object> message = new LinkedHashMap<>(4);
            message.put("module", "device");
            message.put("event", event);
            message.put("data", payload);
            message.put("timestamp", System.currentTimeMillis());
            template.broadcast(TRANSPORT_EVENT, objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            // 实时通道故障不能影响业务主流程
            log.warn("[Device][Realtime] 事件广播失败: event={}, message={}", event, e.getMessage());
        }
    }
}
