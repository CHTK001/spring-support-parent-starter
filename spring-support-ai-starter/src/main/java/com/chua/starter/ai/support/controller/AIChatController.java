package com.chua.starter.ai.support.controller;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.starter.ai.support.chat.ChatMessage;
import com.chua.starter.ai.support.entity.AIDialog;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.service.AIDialogRecordService;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 对话聊天控制器。
 * <p>
 * 提供会话管理（创建/删除/查询）和基于 SSE 的流式聊天能力。
 *
 * @author CH
 */
@Tag(name = "AI Chat Dialog Controller", description = "AI 对话聊天接口")
@RestController
@RequiredArgsConstructor
@Slf4j
@ApiReturnFormatIgnore
public class AIChatController {

    private final AIDialogRecordService aiDialogRecordService;
    private final AiChatFacadeService aiChatFacadeService;

    // ==================== 会话管理 ====================

    /**
     * 创建新会话。
     */
    @Operation(summary = "创建会话", description = "创建新的对话会话")
    @PostMapping("/provider/ai/chat/dialog")
    public ResponseEntity<Map<String, Object>> createDialog(@RequestParam(required = false, defaultValue = "新对话") String name) {
        AIDialog dialog = aiDialogRecordService.createDialog(name);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", dialog.getId());
        result.put("name", dialog.getName());
        result.put("timestamp", dialog.getTimestamp());
        return ResponseEntity.ok(result);
    }

    /**
     * 删除指定会话。
     */
    @Operation(summary = "删除会话", description = "删除指定对话会话及其所有消息")
    @DeleteMapping("/provider/ai/chat/dialog/{dialogId}")
    public ResponseEntity<Map<String, Object>> deleteDialog(@PathVariable Long dialogId) {
        aiDialogRecordService.deleteDialog(dialogId);
        return ResponseEntity.ok(Map.of("success", true, "dialogId", dialogId));
    }

    /**
     * 获取所有会话列表。
     */
    @Operation(summary = "获取会话列表", description = "获取所有对话会话列表")
    @GetMapping("/provider/ai/chat/dialog")
    public ResponseEntity<List<AIDialog>> getDialog() {
        List<AIDialog> dialogs = aiDialogRecordService.listDialog();
        return ResponseEntity.ok(dialogs);
    }

    /**
     * 获取指定会话的消息记录。
     */
    @Operation(summary = "获取会话消息", description = "获取指定会话的所有消息记录")
    @GetMapping("/provider/ai/chat/dialog/{dialogId}")
    public ResponseEntity<List<ChatMessage>> getDialogRecord(@PathVariable Long dialogId) {
        List<ChatMessage> messages = aiDialogRecordService.getDialogMessages(dialogId);
        return ResponseEntity.ok(messages);
    }

    // ==================== 聊天（SSE 流式） ====================

    /**
     * 流式聊天接口。
     * <p>
     * 将用户消息保存到会话记录，调用 AI 门面服务进行流式推理，
     * 并将 AI 回复也保存到消息记录。
     */
    @Operation(summary = "AI 聊天", description = "向指定会话发送消息并获取 AI 流式回复（SSE）")
    @PostMapping(value = "/provider/ai/chat/{dialogId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @PathVariable Long dialogId,
            @RequestBody(required = false) List<ChatMessage> messages,
            @RequestParam(required = false) List<MultipartFile> files) {

        // 保存用户消息到记录
        if (messages != null) {
            for (ChatMessage msg : messages) {
                aiDialogRecordService.addMessage(dialogId, msg);
            }
        }

        // 构建 AiChatRequest
        AiChatRequest request = new AiChatRequest();
        if (messages != null && !messages.isEmpty()) {
            List<Map<String, Object>> msgList = new ArrayList<>();
            for (ChatMessage msg : messages) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("role", msg.getRole());
                map.put("content", msg.getContent());
                msgList.add(map);
            }
            request.setMessages(msgList);
        }

        // 调用 AI 门面进行流式回复，收集完整回复用于保存
        StringBuilder assistantReply = new StringBuilder();
        SseEmitter facadeEmitter = aiChatFacadeService.stream(request);

        // 在 facadeEmitter 完成后保存 AI 回复
        facadeEmitter.onCompletion(() -> {
            if (assistantReply.length() > 0) {
                aiDialogRecordService.addMessage(dialogId, ChatMessage.assistant(assistantReply.toString()));
            }
        });

        return facadeEmitter;
    }
}

