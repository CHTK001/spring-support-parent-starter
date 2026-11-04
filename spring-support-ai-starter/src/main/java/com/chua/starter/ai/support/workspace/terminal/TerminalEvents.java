package com.chua.starter.ai.support.workspace.terminal;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 终端 WebSocket 事件发送工具。
 *
 * <p>终端会话与连接已解耦，同一个 PTY 会被多个连接附着，因此事件发送必须集中在这里：
 * 统一 {@code {event,data}} 报文结构、统一 {@code synchronized (session)} 串行发送，
 * 避免并发写同一个 WebSocket 会话触发容器级异常。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
final class TerminalEvents {

    /**
     * 事件名：终端就绪
     */
    static final String EVENT_READY = "terminal.ready";

    /**
     * 事件名：终端输出
     */
    static final String EVENT_OUTPUT = "terminal.output";

    /**
     * 事件名：进程退出
     */
    static final String EVENT_EXIT = "terminal.exit";

    /**
     * 事件名：进程被主动终止
     */
    static final String EVENT_STOPPED = "terminal.stopped";

    /**
     * 事件名：终端错误
     */
    static final String EVENT_ERROR = "terminal.error";

    /**
     * 报文键：事件名
     */
    private static final String KEY_EVENT = "event";

    /**
     * 报文键：事件数据
     */
    private static final String KEY_DATA = "data";

    /**
     * 报文键：输出片段
     */
    private static final String KEY_OUTPUT = "data";

    /**
     * 报文外层容量：event + data 两个键
     */
    private static final int PAYLOAD_CAPACITY = 2;

    /**
     * 输出事件数据容量：只有一个 data 键
     */
    private static final int OUTPUT_CAPACITY = 1;

    /**
     * 工具类禁止实例化
     */
    private TerminalEvents() {
    }

    /**
     * 构建输出事件数据。
     *
     * @param data 原始输出片段（含 ANSI 控制序列）
     * @return 事件数据
     */
    static Map<String, Object> outputData(String data) {
        Map<String, Object> payload = new LinkedHashMap<>(OUTPUT_CAPACITY);
        payload.put(KEY_OUTPUT, data);
        return payload;
    }

    /**
     * 向指定连接发送事件。
     *
     * @param objectMapper JSON 工具
     * @param session      WS 会话
     * @param event        事件名
     * @param data         事件数据
     */
    static void send(ObjectMapper objectMapper, WebSocketSession session, String event, Map<String, Object> data) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>(PAYLOAD_CAPACITY);
            payload.put(KEY_EVENT, event);
            payload.put(KEY_DATA, data);
            String text = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(text));
                }
            }
        } catch (Exception e) {
            log.debug("[terminal] 发送事件失败 event={} error={}", event, messageOf(e));
        }
    }

    /**
     * 发送终端错误事件。
     *
     * @param objectMapper JSON 工具
     * @param session      WS 会话
     * @param message      错误信息
     * @return 是否发送成功
     */
    static boolean sendError(ObjectMapper objectMapper, WebSocketSession session, String message) {
        if (session == null || !session.isOpen()) {
            return false;
        }
        Map<String, Object> data = new LinkedHashMap<>(OUTPUT_CAPACITY);
        data.put("message", message);
        send(objectMapper, session, EVENT_ERROR, data);
        return true;
    }

    /**
     * 异常信息提取。
     *
     * @param throwable 异常
     * @return 异常信息
     */
    static String messageOf(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }
}
