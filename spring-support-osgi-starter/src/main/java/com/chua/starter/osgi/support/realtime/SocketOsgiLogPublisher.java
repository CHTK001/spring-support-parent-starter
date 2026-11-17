package com.chua.starter.osgi.support.realtime;

import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.starter.common.support.text.json.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于宿主 Socket 会话模板的插件操作日志发布器。
 *
 * <p>走<b>全局 socket 通道</b>：统一以 Socket.IO 的 {@code message} 事件广播，
 * 载荷对齐前端 {@code WsMessage}：{@code {module, event, data, dataId, timestamp}}，
 * 前端 {@code socket.subscribe("osgi", "operation-log", handler)} 即可收到。</p>
 *
 * <p><b>为什么广播而不是只推发起人</b>：插件操作改的是整个运行时的状态，同一控制台里
 * 多人同时看着插件列表时都该看到「谁在升级哪个插件、失败原因是什么」。
 * 需要按人过滤时前端用 {@code data.userId} 自己筛，后端不必为每个观察者维护一份订阅。</p>
 *
 * <p>用 {@link ObjectProvider} 延迟解析会话模板：宿主未启用 socket 时自动退化为
 * 本地 debug 日志，不会因为缺少 Bean 导致启动失败。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class SocketOsgiLogPublisher implements OsgiLogPublisher {

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
     * 会话模板提供者（延迟解析）。
     */
    private final ObjectProvider<SocketSessionTemplate> templateProvider;

    /**
     * 创建发布器。
     *
     * @param templateProvider 会话模板提供者
     */
    public SocketOsgiLogPublisher(ObjectProvider<SocketSessionTemplate> templateProvider) {
        this.templateProvider = templateProvider;
    }

    @Override
    public void publish(OsgiLogEvent event) {
        if (event == null) {
            return;
        }
        SocketSessionTemplate template = templateProvider.getIfAvailable();
        if (template == null) {
            log.debug("[OSGi][Log] 宿主未启用 socket，跳过推送: action={}, status={}, symbolicName={}",
                    event.action(), event.status(), event.symbolicName());
            return;
        }
        String payload;
        try {
            payload = Json.toJson(toMessage(event));
        } catch (Exception e) {
            log.warn("[OSGi][Log] 日志序列化失败: action={}, symbolicName={}, reason={}",
                    event.action(), event.symbolicName(), e.getMessage());
            return;
        }
        try {
            template.broadcast(TRANSPORT_EVENT, payload);
        } catch (Exception e) {
            // 日志是旁路：推送失败只降级，绝不能让插件操作因此失败
            log.debug("[OSGi][Log] 日志广播失败: action={}, symbolicName={}, reason={}",
                    event.action(), event.symbolicName(), e.getMessage());
        }
    }

    /**
     * 组装前端可消费的消息体。
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
