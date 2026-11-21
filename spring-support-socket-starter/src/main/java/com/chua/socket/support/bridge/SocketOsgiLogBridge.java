package com.chua.socket.support.bridge;

import com.chua.common.support.lang.json.Json;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.logger.event.OsgiLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OSGi 插件操作日志 → Socket 广播桥。
 *
 * <p>监听 {@link OsgiLogEvent} Spring 应用事件（由 osgi-starter 的日志发布器发布；
 * 事件契约定义在 logger-starter，避免 osgi 直接依赖 socket 形成
 * {@code common -> osgi -> socket -> common} 的依赖环），把插件上传 / 安装 / 升级 /
 * 启停 / 卸载 / 删除 jar 的操作日志广播到全局 socket 连接。</p>
 *
 * <p>走<b>全局 socket 通道</b>：统一以 Socket.IO 的 {@code message} 事件广播，
 * 载荷对齐前端 {@code WsMessage}：{@code {module, event, data, dataId, timestamp}}，
 * 前端 {@code socket.subscribe("osgi", "operation-log", handler)} 即可收到。</p>
 *
 * <p><b>日志是旁路</b>：序列化 / 广播失败只降级为本地日志，绝不能让插件操作因此失败。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocketOsgiLogBridge {

    /**
     * 前端全局 socket 监听的传输事件名。
     */
    private static final String TRANSPORT_EVENT = "message";

    /**
     * 前端订阅用的模块标识。
     */
    public static final String MODULE = "osgi";

    /**
     * 前端订阅用的操作日志事件名。
     */
    public static final String EVENT = "operation-log";

    /**
     * Socket 会话模板提供者（延迟解析，socket 未启用时为空）。
     */
    private final ObjectProvider<SocketSessionTemplate> templateProvider;

    /**
     * 监听插件操作日志事件并广播到全局 socket 连接。
     *
     * @param event 插件操作日志事件
     */
    @EventListener
    public void onOsgiLogEvent(OsgiLogEvent event) {
        if (event == null) {
            return;
        }
        SocketSessionTemplate template = templateProvider.getIfAvailable();
        if (template == null) {
            log.debug("[socket-bridge] Socket 未启用，跳过 OSGi 日志广播: action={}, status={}, symbolicName={}",
                    event.action(), event.status(), event.symbolicName());
            return;
        }
        String payload;
        try {
            payload = Json.toJson(toMessage(event));
        } catch (Exception e) {
            log.warn("[socket-bridge] OSGi 日志序列化失败: action={}, symbolicName={}, reason={}",
                    event.action(), event.symbolicName(), e.getMessage());
            return;
        }
        try {
            template.broadcast(TRANSPORT_EVENT, payload);
        } catch (Exception e) {
            // 日志是旁路：推送失败只降级，绝不能让插件操作因此失败
            log.debug("[socket-bridge] OSGi 日志广播失败: action={}, symbolicName={}, reason={}",
                    event.action(), event.symbolicName(), e.getMessage());
        }
    }

    /**
     * 组装前端可消费的消息体（载荷与原 osgi 侧 SocketOsgiLogPublisher 完全一致）。
     *
     * <p>{@code data} 里同时铺「谁、做了什么、结果如何、花了多久」，前端一套日志组件
     * 就能渲染任意模块的操作流，不必为 OSGi 单独写解析。</p>
     *
     * @param event 日志事件
     * @return 包含 module / event / data / dataId / timestamp 的消息映射
     */
    Map<String, Object> toMessage(OsgiLogEvent event) {
        Map<String, Object> data = new LinkedHashMap<>(12);
        data.put("level", event.level());
        data.put("status", event.status());
        data.put("action", event.action());
        data.put("symbolicName", event.symbolicName());
        data.put("message", event.message());
        data.put("startedAt", event.startedAt());
        data.put("duration", event.duration());
        data.put("timestamp", event.startedAt() + event.duration());

        Map<String, Object> message = new LinkedHashMap<>(6);
        message.put("module", MODULE);
        message.put("event", EVENT);
        message.put("data", data);
        message.put("dataId", event.symbolicName());
        message.put("timestamp", event.startedAt() + event.duration());
        return message;
    }
}
