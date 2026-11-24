package com.chua.socket.support.bridge;

import com.chua.common.support.lang.json.Json;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.logger.event.OsgiStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OSGi bundle 状态 → Socket 广播桥。
 *
 * <p>监听 {@link OsgiStateEvent} Spring 应用事件（由 osgi-starter 的状态事件桥发布；
 * 事件契约定义在 logger-starter，避免 osgi 直接依赖 socket 形成
 * {@code common -> osgi -> socket -> common} 的依赖环），把 bundle 的安装、启动、
 * 停止、更新、卸载、状态变化与解析失败广播到全局 socket 连接。</p>
 *
 * <p>走<b>全局 socket 通道</b>：统一以 Socket.IO 的 {@code message} 事件广播，
 * 载荷对齐前端 {@code WsMessage}：{@code {module, event, data, dataId, timestamp}}，
 * 前端 {@code socket.subscribe("osgi", "bundle-state", handler)} 即可收到，
 * 据此实时刷新 bundle 状态墙，不必整表轮询。</p>
 *
 * <p><b>状态推送是旁路</b>：序列化 / 广播失败只降级为本地日志，绝不能让插件操作因此失败。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocketOsgiStateBridge {

    /**
     * 前端全局 socket 监听的传输事件名。
     */
    private static final String TRANSPORT_EVENT = "message";

    /**
     * 前端订阅用的模块标识。
     */
    public static final String MODULE = "osgi";

    /**
     * 前端订阅用的状态推送事件名。
     */
    public static final String EVENT = "bundle-state";

    /**
     * Socket 会话模板提供者（延迟解析，socket 未启用时为空）。
     */
    private final ObjectProvider<SocketSessionTemplate> templateProvider;

    /**
     * 监听 bundle 状态事件并广播到全局 socket 连接。
     *
     * @param event bundle 状态事件
     */
    @EventListener
    public void onOsgiStateEvent(OsgiStateEvent event) {
        if (event == null) {
            return;
        }
        SocketSessionTemplate template = templateProvider.getIfAvailable();
        if (template == null) {
            log.debug("[socket-bridge] Socket 未启用，跳过 OSGi 状态广播: action={}, symbolicName={}",
                    event.action(), event.symbolicName());
            return;
        }
        String payload;
        try {
            payload = Json.toJson(toMessage(event));
        } catch (Exception e) {
            log.warn("[socket-bridge] OSGi 状态序列化失败: action={}, symbolicName={}, reason={}",
                    event.action(), event.symbolicName(), e.getMessage());
            return;
        }
        try {
            template.broadcast(TRANSPORT_EVENT, payload);
        } catch (Exception e) {
            // 状态推送是旁路：广播失败只降级，绝不能让插件操作因此失败
            log.debug("[socket-bridge] OSGi 状态广播失败: action={}, symbolicName={}, reason={}",
                    event.action(), event.symbolicName(), e.getMessage());
        }
    }

    /**
     * 组装前端可消费的消息体。
     *
     * <p>{@code data} 里铺「什么动作、哪个插件、状态从哪变到哪、附加说明」，
     * 前端按 {@code action + newState} 原地更新状态墙即可。</p>
     *
     * @param event 状态事件
     * @return 包含 module / event / data / dataId / timestamp 的消息映射
     */
    Map<String, Object> toMessage(OsgiStateEvent event) {
        Map<String, Object> data = new LinkedHashMap<>(8);
        data.put("action", event.action());
        data.put("symbolicName", event.symbolicName());
        data.put("oldState", event.oldState());
        data.put("newState", event.newState());
        data.put("version", event.version());
        data.put("message", event.message());
        data.put("timestamp", event.timestamp());

        Map<String, Object> message = new LinkedHashMap<>(6);
        message.put("module", MODULE);
        message.put("event", EVENT);
        message.put("data", data);
        message.put("dataId", event.symbolicName());
        message.put("timestamp", event.timestamp());
        return message;
    }
}
