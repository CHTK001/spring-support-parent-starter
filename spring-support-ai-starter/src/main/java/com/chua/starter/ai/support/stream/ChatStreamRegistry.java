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
     * sessionId -> 本轮 Agent 的中断动作。
     *
     * <p>停止会话时必须有这一环：关帧槽只是“不再往前端推”，
     * 在跑的 Agent（opencode 子进程、agentscope Harness、langchain4j 循环）本身不会停。
     * 由构建侧在每轮开始时绑定，停止或轮次结束时解除。</p>
     */
    private final Map<String, Runnable> interrupters = new ConcurrentHashMap<>();

    /**
     * 绑定本轮 Agent 的中断动作。
     *
     * @param sessionId   会话标识，为 null 时忽略
     * @param interrupter 中断动作（通常是 {@code AgentChatClient::interrupt}），为 null 时忽略
     */
    public void bindInterrupter(String sessionId, Runnable interrupter) {
        if (sessionId != null && interrupter != null) {
            interrupters.put(sessionId, interrupter);
        }
    }

    /**
     * 解除绑定（仅当当前绑定就是这一个动作时才移除，避免误删下一轮的新绑定）。
     *
     * @param sessionId   会话标识
     * @param interrupter 当初绑定的中断动作
     */
    public void unbindInterrupter(String sessionId, Runnable interrupter) {
        if (sessionId != null && interrupter != null) {
            interrupters.remove(sessionId, interrupter);
        }
    }

    /**
     * 中断指定会话正在跑的 Agent。
     *
     * @param sessionId 会话标识
     * @return true 表示确实发出了中断
     */
    public boolean interruptAgent(String sessionId) {
        Runnable action = sessionId == null ? null : interrupters.remove(sessionId);
        if (action == null) {
            return false;
        }
        try {
            action.run();
            log.info("[ChatStream] 已中断会话执行 sessionId={}", sessionId);
            return true;
        } catch (Exception e) {
            log.warn("[ChatStream] 中断会话执行失败 sessionId={}: {}", sessionId, e.getMessage());
            return false;
        }
    }

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
            interrupters.remove(sessionId);
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
        // 先真正停掉在执行的那一轮：只关帧槽的话，子进程/循环还在后台空转
        interruptAgent(sessionId);
        sink.markCancelled();
        try {
            sink.sendEvent(java.util.Map.of("type", "finish", "finishReason", "stop"));
            sink.sendEvent(java.util.Map.of("type", "turn_end", "finishReason", "stop"));
            sink.sendDone();
        } catch (Exception e) {
            log.debug("[ai-stream] 发送结束帧失败，已忽略: {}", e.getMessage());
        }
        active.remove(sessionId);
        sink.close();
        log.info("[ChatStream] 已停止流式 sessionId={}", sessionId);
    }
}
