package com.chua.starter.ai.support.stream;

import com.chua.common.support.ai.agent.AgentTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 帧槽：把帧写成裸 {@code data:} / {@code event: trace} 字节并即时 flush。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class SseChatFrameSink implements ChatFrameSink {

    /**
     * 会话专属输出流
     */
    private final OutputStream out;

    /**
     * JSON 工具
     */
    private final ObjectMapper objectMapper;

    /**
     * 结束回调（完成 SseEmitter）
     */
    private final Runnable onClose;

    /**
     * 取消标记
     */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * 构造。
     *
     * @param objectMapper JSON 工具
     * @param out          会话专属输出流，不允许为 null
     * @param onClose      结束回调（完成 emitter），可空
     */
    public SseChatFrameSink(ObjectMapper objectMapper, OutputStream out, Runnable onClose) {
        this.objectMapper = objectMapper;
        this.out = out;
        this.onClose = onClose;
    }

    @Override
    public void sendEvent(Map<String, Object> event) {
        try {
            write("data: " + objectMapper.writeValueAsString(event) + "\n\n");
        } catch (Exception e) {
            log.debug("[ChatStream] 发送事件失败: {}", e.getMessage());
        }
    }

    @Override
    public void sendTrace(AgentTrace trace) {
        if (trace == null) {
            return;
        }
        try {
            write("event: trace\ndata: " + objectMapper.writeValueAsString(trace) + "\n\n");
        } catch (Exception e) {
            log.debug("[ChatStream] 发送 trace 失败: {}", e.getMessage());
        }
    }

    @Override
    public void sendDone() {
        write("data: [DONE]\n\n");
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void markCancelled() {
        cancelled.set(true);
    }

    @Override
    public void close() {
        if (onClose != null) {
            try {
                onClose.run();
            } catch (Exception e) {
                log.debug("[ai-sse] 关闭回调失败，已忽略: {}", e.getMessage());
            }
        }
    }

    /**
     * 写字节并即时 flush。
     *
     * @param s 帧内容
     */
    private void write(String s) {
        try {
            out.write(s.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            log.debug("[ChatStream] 写输出流失败: {}", e.getMessage());
        }
    }
}
