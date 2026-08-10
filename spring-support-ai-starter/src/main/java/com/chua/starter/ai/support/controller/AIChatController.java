package com.chua.starter.ai.support.controller;

import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import com.chua.starter.ai.support.chat.ChatMessage;
import com.chua.starter.ai.support.entity.AIDialog;
import com.chua.starter.ai.support.entity.SysAiSessionCompression;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.service.AIDialogRecordService;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.SysAiSessionCompressionService;
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
import org.springframework.web.bind.annotation.RequestBody;
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
 * 继承 {@link BaseAiController} 以便统一使用基础服务能力。
 *
 * @author CH
 */
@Tag(name = "AI Chat Dialog Controller", description = "AI 对话聊天接口")
@RestController
@RequiredArgsConstructor
@Slf4j
@ApiReturnFormatIgnore
public class AIChatController extends BaseAiController {

    private final AIDialogRecordService aiDialogRecordService;
    private final AiChatFacadeService aiChatFacadeService;
    private final SysAiSessionCompressionService aiSessionCompressionService;
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    // ==================== 会话管理 ====================

    /**
     * 创建新会话。
     */
    @Operation(summary = "创建会话", description = "创建新的对话会话")
    @Permission("admin")
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
    @Permission("admin")
    @DeleteMapping("/provider/ai/chat/dialog/{dialogId}")
    public ResponseEntity<Map<String, Object>> deleteDialog(@PathVariable Long dialogId) {
        aiDialogRecordService.deleteDialog(dialogId);
        return ResponseEntity.ok(Map.of("success", true, "dialogId", dialogId));
    }

    /**
     * 获取所有会话列表。
     */
    @Operation(summary = "获取会话列表", description = "获取所有对话会话列表")
    @Permission("admin")
    @GetMapping("/provider/ai/chat/dialog")
    public ResponseEntity<List<AIDialog>> getDialog() {
        List<AIDialog> dialogs = aiDialogRecordService.listDialog();
        return ResponseEntity.ok(dialogs);
    }

    /**
     * 获取指定会话的消息记录。
     */
    @Operation(summary = "获取会话消息", description = "获取指定会话的所有消息记录")
    @Permission("admin")
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
    @Permission("admin")
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
        List<Map<String, Object>> msgList = new ArrayList<>();
        // 命中会话压缩上下文时，用「摘要 + 最近 N 轮」压缩结果替代全量历史，真正降低发送给模型的 token 量
        SysAiSessionCompression compression = aiSessionCompressionService.getBySessionId(dialogId);
        if (compression != null && compression.getSysAiCompressionContext() != null
                && !compression.getSysAiCompressionContext().isEmpty()) {
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("role", "system");
            ctx.put("content", "以下是之前对话的摘要上下文：\n" + compression.getSysAiCompressionContext());
            msgList.add(ctx);
            // 仅追加本轮最新的用户消息，保证当前问题被回答
            if (messages != null && !messages.isEmpty()) {
                ChatMessage last = messages.get(messages.size() - 1);
                if (last != null && last.getContent() != null && !last.getContent().isEmpty()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("role", last.getRole() != null ? last.getRole() : "user");
                    map.put("content", last.getContent());
                    msgList.add(map);
                }
            }
            log.debug("[AIChat] 会话 {} 命中压缩上下文，用压缩摘要替代 {} 条历史", dialogId, messages != null ? messages.size() : 0);
        } else if (messages != null && !messages.isEmpty()) {
            // 未命中压缩上下文：使用前端传入的完整历史消息
            for (ChatMessage msg : messages) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("role", msg.getRole());
                map.put("content", msg.getContent());
                msgList.add(map);
            }
        }
        if (!msgList.isEmpty()) {
            request.setMessages(msgList);
        }

        // 调用 AI 门面进行流式回复，收集完整回复保存为 assistant 消息并更新压缩上下文
        return aiChatFacadeService.stream(request, reply -> {
            if (reply != null && !reply.isEmpty()) {
                aiDialogRecordService.addMessage(dialogId, ChatMessage.assistant(reply));
            }
            // 回复完成后，将历史压缩为「摘要 + 最近 N 轮」写入上下文表
            compressAndSaveContext(dialogId);
        });
    }

    /**
     * 将历史消息压缩为摘要并写入会话压缩上下文表。
     *
     * <p>规则：会话累计达到 {@code chat_compress_trigger_rounds} 轮后才触发压缩，
     * 保留最近 {@code chat_compress_retain_rounds} 轮原始对话，之前的历史压缩为摘要，
     * 以「摘要 + 最近 N 轮原始对话」拼接后作为后续聊天上下文落库。</p>
     *
     * <p>阈值与保留轮数均可通过全局设置（runtime 分组）配置：
     * <ul>
     *   <li>{@code chat_compress_trigger_rounds} — 触发压缩的累计轮数，默认 100</li>
     *   <li>{@code chat_compress_retain_rounds} — 压缩后保留的最近轮数，默认 6</li>
     * </ul></p>
     *
     * @param dialogId 会话 ID
     */
    private void compressAndSaveContext(Long dialogId) {
        try {
            List<ChatMessage> history = aiDialogRecordService.getDialogMessages(dialogId);
            if (history == null || history.isEmpty()) {
                return;
            }
            // 触发压缩的累计轮数（默认 100 轮，即 200 条消息）与保留的最近轮数（默认 6 轮）
            int triggerRounds = parseIntSetting("chat_compress_trigger_rounds", 100);
            int retainRounds = parseIntSetting("chat_compress_retain_rounds", 6);
            if (triggerRounds <= 0) {
                triggerRounds = 100;
            }
            if (retainRounds <= 0) {
                retainRounds = 6;
            }
            int retainCount = retainRounds * 2;
            // 会话轮数未达到触发阈值时不压缩（上下文表保持未命中状态，直接用历史）
            if (history.size() <= triggerRounds * 2) {
                return;
            }
            List<ChatMessage> head = new ArrayList<>(history.subList(0, history.size() - retainCount));
            List<ChatMessage> retained = new ArrayList<>(history.subList(history.size() - retainCount, history.size()));
            // 头部（超出最近 N 轮的）压缩为摘要
            StringBuilder summary = new StringBuilder();
            for (ChatMessage msg : head) {
                String role = msg.getRole() != null ? msg.getRole() : "user";
                String content = msg.getContent();
                if (content != null && !content.isEmpty()) {
                    summary.append("[").append(role).append("]: ").append(content).append("\n");
                }
            }
            // 拼接：摘要 + 最近 N 轮原始对话
            StringBuilder context = new StringBuilder();
            if (summary.length() > 0) {
                context.append("[Compressed Context]\n")
                        .append(summary.length() > 4000 ? summary.substring(0, 4000) : summary);
            }
            for (ChatMessage msg : retained) {
                String role = msg.getRole() != null ? msg.getRole() : "user";
                String content = msg.getContent();
                if (content != null && !content.isEmpty()) {
                    context.append("[").append(role).append("]: ").append(content).append("\n");
                }
            }
            if (context.length() == 0) {
                return;
            }
            // 上下文上限，防止无限膨胀
            String finalContext = context.length() > 16000 ? context.substring(0, 16000) : context.toString();
            SysAiSessionCompression compression = new SysAiSessionCompression();
            compression.setSysAiSessionId(dialogId);
            compression.setSysAiCompressionMessageCount((long) history.size());
            compression.setSysAiCompressionContext(finalContext);
            aiSessionCompressionService.saveOrUpdate(compression);
            log.info("[AIChat] 会话 {} 压缩上下文已保存：摘要头 {} 条，保留最近 {} 轮（{} 条），消息总数 {}",
                    dialogId, head.size(), retainRounds, retained.size(), history.size());
        } catch (Exception e) {
            log.warn("[AIChat] 会话 {} 压缩上下文保存失败: {}", dialogId, e.getMessage());
        }
    }

    /**
     * 从全局设置读取整数配置，解析失败或未配置时返回默认值。
     *
     * @param field        配置字段名（runtime 分组）
     * @param defaultValue 默认值
     * @return 配置值
     */
    private int parseIntSetting(String field, int defaultValue) {
        return sysAiGlobalSettingService.getValue("runtime", field)
                .map(value -> {
                    try {
                        return Integer.parseInt(value.trim());
                    } catch (NumberFormatException ignored) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }
}

