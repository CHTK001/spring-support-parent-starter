package com.chua.starter.ai.support.stream;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活跃聊天流注册表（SSE 与 WebSocket 共用）。
 *
 * <p>按会话 sessionId 登记当前活跃轮次的归属人与帧槽，供停止逻辑统一处理：
 * 校验归属 → 标记取消 → 下发 finish/turn_end/done → 结束传输。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class ChatStreamRegistry {

    /**
     * 活跃轮次。
     *
     * @param ownerUserId 归属用户标识
     * @param sink        帧槽
     */
    public record ActiveChat(String ownerUserId, ChatFrameSink sink) {
    }

    /**
     * sessionId -> 活跃轮次
     */
    private final Map<String, ActiveChat> active = new ConcurrentHashMap<>();

    /**
     * 登记活跃轮次。
     *
     * @param sessionId   会话标识，不允许为 null
     * @param ownerUserId 归属用户标识，可空（机器流量）
     * @param sink        帧槽，不允许为 null
     */
    public void register(String sessionId, String ownerUserId, ChatFrameSink sink) {
        if (sessionId != null) {
            active.put(sessionId, new ActiveChat(ownerUserId, sink));
        }
    }

    /**
     * 移除活跃轮次。
     *
     * @param sessionId 会话标识
     */
    public void remove(String sessionId) {
        if (sessionId != null) {
            active.remove(sessionId);
        }
    }

    /**
     * 读取活跃轮次。
     *
     * @param sessionId 会话标识
     * @return 活跃轮次，不存在返回 {@code null}
     */
    public ActiveChat get(String sessionId) {
        return sessionId == null ? null : active.get(sessionId);
    }

    /**
     * 请求停止指定会话的活跃轮次。
     *
     * <p>仅发起方可停止：owner 非空且与当前用户不一致直接 403。</p>
     *
     * @param sessionId 会话标识，不允许为 null
     * @param actorUserId 操作人标识
     */
    public void requestStop(String sessionId, String actorUserId) {
        ActiveChat chat = active.get(sessionId);
        if (chat == null) {
            return;
        }
        String owner = chat.ownerUserId();
        if (owner != null && !owner.equals(actorUserId)) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权停止他人会话的流式输出");
        }
        ChatFrameSink sink = chat.sink();
        sink.markCancelled();
        try {
            sink.sendEvent(java.util.Map.of("type", "finish", "finishReason", "stop"));
            sink.sendEvent(java.util.Map.of("type", "turn_end", "finishReason", "stop"));
            sink.sendDone();
        } catch (Exception ignored) {
        }
        active.remove(sessionId);
        sink.close();
        log.info("[ChatStream] 已停止流式 sessionId={}", sessionId);
    }
}
