package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.chat.ChatMemorySupport;
import com.chua.starter.ai.support.chat.ChatMessage;
import com.chua.starter.ai.support.entity.AIDialog;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.service.AIDialogRecordService;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.util.AiUserContext;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天发送接口（前端适配）。
 *
 * <p>POST /v2/chat/send：非 SSE，异步触发一次模型对话并落库，立即返回。
 * 真正的流式回复走 {@link ChatStreamController} 的 /v2/chat/stream。</p>
 */
@RestController
@RequestMapping("/v2/chat")
@ApiReturnFormatIgnore
@RequiredArgsConstructor
@Slf4j
public class ChatSendController {

    private static final String DEFAULT_MODEL = "agnes-2.5-flash";

    private final AiChatFacadeService aiChatFacadeService;
    private final AIDialogRecordService aiDialogRecordService;
    private final ChatMemorySupport chatMemorySupport;

    /**
     * 前端 sessionId(UUID) -> 后端 dialogId 映射
    */
    private final Map<String, Long> sessionDialogMap = new ConcurrentHashMap<>();

    /**
     * 发送消息（非流式，异步触发模型对话并落库）。
     * @param request 请求，不允许为 null
     * @return 结果映射，无数据时为空映射
     */
    @PostMapping("/send")
    public Map<String, Object> send(@RequestBody Map<String, Object> request) {
        log.info("[ChatSend] 收到消息: {}", request);

        String sessionId = str(request.get("sessionId"));
        String content = str(request.get("content"));
        if (content == null || content.isBlank()) {
            Object userMessage = request.get("userMessage");
            if (userMessage instanceof Map<?, ?> um) {
                content = str(um.get("content"));
            }
        }
        if (content == null || content.isBlank()) {
            return Map.of("success", false, "message", "消息内容为空");
        }

        Long dialogId = resolveDialogId(sessionId);

        // 落库用户消息
        try {
            aiDialogRecordService.addMessage(dialogId, new ChatMessage("user", content));
        } catch (Exception e) {
            log.warn("[ChatSend] 保存用户消息失败: {}", e.getMessage());
        }

        // 异步触发模型对话（非 SSE 等待，复用 stream 的 onComplete 拿完整回复落库）
        final String userContent = content;
        final Long did = dialogId;
        final String sid = sessionId;
        // 身份必须在请求线程内解析：异步回调线程取不到，记忆会落成无主数据
        final String memoryUser = AiUserContext.currentUserId();
        CompletableFuture.runAsync(AiUserContext.capturing(() -> {
            try {
                List<Map<String, Object>> msgList = new ArrayList<>();
                List<ChatMessage> history = aiDialogRecordService.getDialogMessages(did);
                if (history != null) {
                    for (ChatMessage m : history) {
                        Map<String, Object> mm = new LinkedHashMap<>();
                        mm.put("role", m.getRole() != null ? m.getRole() : "user");
                        mm.put("content", m.getContent() != null ? m.getContent() : "");
                        msgList.add(mm);
                    }
                }
                chatMemorySupport.inject(memoryUser, sid, userContent, msgList);
                AiChatRequest req = new AiChatRequest();
                req.setModel(DEFAULT_MODEL);
                req.setStream(true);
                req.setUseSkill(true);
                req.setMessages(msgList);
                // 返回的 emitter 不挂到 HTTP 响应，仅利用回调拿完整回复
                aiChatFacadeService.stream(req, null, reply -> {
                    String finalText = reply != null ? reply : "";
                    try {
                        aiDialogRecordService.addMessage(did, new ChatMessage("assistant", finalText));
                    } catch (Exception e) {
                        log.warn("[ChatSend] 保存助手消息失败: {}", e.getMessage());
                    }
                    chatMemorySupport.remember(memoryUser, sid, userContent, finalText, DEFAULT_MODEL);
                    log.info("[ChatSend] 异步对话完成 sessionId={}, 回复长度={}", sid, finalText.length());
                }, error -> log.error("[ChatSend] 异步对话失败 sessionId={}", sid, error));
            } catch (Exception e) {
                log.error("[ChatSend] 异步对话启动失败 sessionId={}", sid, e);
            }
        }));

        return Map.of(
            "success", true,
            "message", "消息已接收"
        );
    }

    /**
     * 解析DialogID。
     *
     * @param sessionId 会话ID，不允许为 null
     * @return Long 对象
     */
    private Long resolveDialogId(String sessionId) {
        if (sessionId != null && sessionId.matches("\\d+")) {
            return Long.parseLong(sessionId);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            Long cached = sessionDialogMap.get(sessionId);
            if (cached != null) {
                return cached;
            }
        }
        String title = "guada-" + (sessionId != null && sessionId.length() > 8 ? sessionId.substring(0, 8) : "chat");
        AIDialog dialog = aiDialogRecordService.createDialog(title);
        Long id = dialog.getId();
        if (sessionId != null && !sessionId.isBlank()) {
            sessionDialogMap.put(sessionId, id);
        }
        return id;
    }

    /**
     * 字符串。
     *
     * @param o 方法入参 o
     * @return 结果字符串
     */
    private static String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }
}
