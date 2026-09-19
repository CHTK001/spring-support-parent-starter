package com.chua.starter.server.support.push;

import com.chua.starter.common.support.text.json.Json;
import com.chua.socket.support.session.SocketSessionTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务端实时推送封装
 * <p>
 * 直接经 {@link SocketSessionTemplate} 以 Socket.IO 的 "message" 事件广播，
 * 载荷对齐前端 WsMessage：{@code {module, event, data, dataId?, timestamp}}，
 * 前端通过 {@code socket.subscribe(module, event)} 按 "module_event" 接收。
 * </p>
 * <p>
 * 与 monitor 模块经消息队列转发的链路等价，但本模块（项目操作/实时日志）
 * 直接广播，延迟更低，且不依赖消息队列是否启用。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerPushService {

    /**
     * 项目操作模块标识
     */
    public static final String MODULE_PROJECT = "project";

    /**
     * 任务事件（步骤/进度/日志/结果，data 内带 phase）
     */
    public static final String EVENT_TASK = "task";

    /**
     * 实时日志事件
     */
    public static final String EVENT_LOG = "log";

    /**
     * Socket 会话模板（socket 未启用时为空）
     */
    private final ObjectProvider<SocketSessionTemplate> socketTemplateProvider;

    /**
     * 通用推送
     *
     * @param module 模块标识
     * @param event  事件名
     * @param data   数据
     * @param dataId 数据标识（前端可按其过滤），可为空
     */
    public void push(String module, String event, Object data, String dataId) {
        try {
            SocketSessionTemplate template = socketTemplateProvider.getIfAvailable();
            if (template == null) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>(6);
            payload.put("module", module);
            payload.put("event", event);
            payload.put("data", data);
            if (dataId != null) {
                payload.put("dataId", dataId);
            }
            payload.put("timestamp", System.currentTimeMillis());
            template.broadcast("message", Json.toJson(payload));
        } catch (Exception e) {
            log.debug("[push] 推送跳过 {}/{}: {}", module, event, e.getMessage());
        }
    }

    /**
     * 推送项目任务消息
     *
     * @param data   任务数据（带 phase）
     * @param taskId 任务标识
     */
    public void pushTask(Object data, String taskId) {
        push(MODULE_PROJECT, EVENT_TASK, data, taskId);
    }

    /**
     * 推送实时日志行
     *
     * @param data      日志数据
     * @param sessionId 日志会话标识
     */
    public void pushLog(Object data, String sessionId) {
        push(MODULE_PROJECT, EVENT_LOG, data, sessionId);
    }
}
