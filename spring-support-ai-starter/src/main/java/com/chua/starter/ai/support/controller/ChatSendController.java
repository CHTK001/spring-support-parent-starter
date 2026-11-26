package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.agent.AgentTrace;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.service.PlatformAccessKeyService;
import com.chua.starter.ai.support.service.AiSessionEventBroadcaster;
import com.chua.starter.ai.support.stream.ChatFrameSink;
import com.chua.starter.ai.support.stream.ChatStreamOrchestrator;
import com.chua.starter.ai.support.stream.ChatStreamRegistry;
import com.chua.starter.ai.support.stream.PreparedChat;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步发送适配器。权限、模型、附件、技能与消息落库统一交给聊天编排器。
 *
 * @author CH
 */
@RestController
@RequestMapping("/v2/chat")
@ApiReturnFormatIgnore
@RequiredArgsConstructor
public class ChatSendController {

    /** 统一聊天编排。 */
    private final ChatStreamOrchestrator orchestrator;
    /** 活跃执行登记。 */
    private final ChatStreamRegistry registry;
    /** 平台模型访问校验。 */
    private final PlatformAccessKeyService platformAccessKeyService;
    /** 跨页面执行事件。 */
    private final AiSessionEventBroadcaster broadcaster;

    /**
     * 接收消息并异步执行；准备失败直接返回错误，不吞掉落库异常。
     *
     * @param request 聊天参数
     * @param onnxKey ONNX 访问密钥
     * @param webKey Web 访问密钥
     * @return 接收确认
     */
    @PostMapping("/send")
    public Map<String, Object> send(@RequestBody Map<String, Object> request,
            @RequestHeader(value = PlatformAccessKeyService.HEADER_ONNX_APP_KEY, required = false) String onnxKey,
            @RequestHeader(value = PlatformAccessKeyService.HEADER_WEB_APP_KEY, required = false) String webKey) {
        synchronized (registry.lockFor(request.get("sessionId"))) {
            return sendLocked(request, onnxKey, webKey);
        }
    }

    /**
     * 在会话锁内完成准备与登记。
     * @param request 聊天参数
     * @param onnxKey ONNX 访问密钥
     * @param webKey Web 访问密钥
     * @return 接收确认
     */
    private Map<String, Object> sendLocked(Map<String, Object> request, String onnxKey, String webKey) {
        AiUserContext.requireUserId();
        Object content = request.get("content");
        if (request.get("userMessage") instanceof Map<?, ?> message) content = message.get("content");
        if (content == null || String.valueOf(content).isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "消息内容为空");
        }
        Object model = request.get("modelId");
        if (model == null) model = request.get("model");
        platformAccessKeyService.assertChatModelAllowed(model == null ? null : String.valueOf(model), onnxKey, webKey);
        PreparedChat prepared = orchestrator.prepare(request);
        BackgroundSink sink = new BackgroundSink(prepared);
        registry.register(prepared.sessionId(), prepared.memoryUser(), sink);
        broadcaster.publish(AiSessionEventBroadcaster.TYPE_STREAM_STARTED,
                prepared.memoryUser(), prepared.sessionId(), null);
        orchestrator.execute(prepared, sink);
        return Map.of("success", true, "message", "消息已接收");
    }

    /** 无 HTTP 流的真实运行槽，仍参与停止与终态广播。 */
    private final class BackgroundSink implements ChatFrameSink {
        /** 当前运行。 */
        private final PreparedChat prepared;
        /** 取消标记。 */
        private final AtomicBoolean cancelled = new AtomicBoolean();
        /** 单次终态。 */
        private final AtomicBoolean finished = new AtomicBoolean();
        /** 结束原因。 */
        private String reason = "completed";

        /** @param prepared 当前运行 */
        private BackgroundSink(PreparedChat prepared) { this.prepared = prepared; }
        @Override
        public void sendEvent(Map<String, Object> event) {
            if ("finish".equals(event.get("type"))) {
                String value = String.valueOf(event.getOrDefault("finishReason", "stop"));
                reason = "stop".equals(value) ? "completed" : value;
            }
        }
        @Override
        public void sendTrace(AgentTrace trace) { }
        @Override
        public void sendDone() { close(); }
        @Override
        public boolean isCancelled() { return cancelled.get(); }
        @Override
        public void markCancelled() { cancelled.set(true); }
        @Override
        public void close() {
            if (finished.compareAndSet(false, true)) {
                registry.remove(prepared.sessionId(), this);
                broadcaster.publish(AiSessionEventBroadcaster.TYPE_STREAM_FINISHED,
                        prepared.memoryUser(), prepared.sessionId(), Map.of("reason", reason));
            }
        }
    }
}
