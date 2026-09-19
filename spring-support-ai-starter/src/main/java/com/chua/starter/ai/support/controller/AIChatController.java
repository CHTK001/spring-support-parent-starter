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
import jakarta.servlet.http.HttpServletRequest;
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
 * @since 4.0.0
 */
@Tag(name = "AI Chat Dialog Controller", description = "AI 对话聊天接口")
@RestController
@RequiredArgsConstructor
@Slf4j
@ApiReturnFormatIgnore
public class AIChatController extends BaseAiController {

    /**
     * 压缩上下文拼接时的摘要最大长度
     */
    private static final int SUMMARY_MAX_LENGTH = 4000;

    /**
     * 压缩上下文总量上限
     */
    private static final int COMPRESSED_CONTEXT_MAX_LENGTH = 16000;

    /**
     * 摘要模型单次可接收的历史文本上限，超出部分先截断再送入模型
     */
    private static final int SUMMARY_INPUT_MAX_LENGTH = 12000;

    /**
     * 生成对话摘要时使用的系统提示词
     */
    private static final String SUMMARY_SYSTEM_PROMPT =
            "你是对话历史压缩器。请把给定的历史对话压缩成简洁的中文摘要，必须保留：用户的关键事实与偏好、"
                    + "已得出的结论与决策、仍在进行中的任务和未回答的问题；忽略寒暄、重复表述与冗余示例。"
                    + "只输出摘要正文，不要输出解释、前言或列表以外的额外内容，长度控制在 800 字以内。";

    /** aidialogrecord服务 */
    private final AIDialogRecordService aiDialogRecordService;
    /** aichatfacade服务 */
    private final AiChatFacadeService aiChatFacadeService;
    /** AI会话compression服务 */
    private final SysAiSessionCompressionService aiSessionCompressionService;
    /** SYSAI全局设置服务 */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    // ==================== 会话管理 ====================

    /**
     * 创建新会话。
     * @param "新对话" "新对话"
     * @return 创建dialog的结果
     * @param "新对话" "新对话"
     * @param "新对话" "新对话"
     * @param name 名称，不允许为 null
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
     * @param dialogId dialogid
     * @return 删除dialog的结果
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
     * @return 获取dialog的结果
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
     * @param dialogId dialogid
     * @return 获取dialogrecord的结果
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
            @RequestParam(required = false) String model,
            @RequestBody(required = false) List<ChatMessage> messages,
            @RequestParam(required = false) List<MultipartFile> files,
            HttpServletRequest servletRequest) {

        // 保存用户消息到记录
        if (messages != null) {
            for (ChatMessage msg : messages) {
                aiDialogRecordService.addMessage(dialogId, msg);
            }
        }

 // 构建 AI对话请求
        AiChatRequest request = new AiChatRequest();
        // 不指定模型时门面只能落到空的聚合默认客户端，对话与压缩都不会真正执行
        String chatModel = resolveChatModel(model);
        request.setModel(chatModel);
        List<Map<String, Object>> msgList = new ArrayList<>();
 // 命中会话压缩上下文时，用「摘要 + 最近 N 轮」压缩结果替代全量历史，真正降低发送给模型的 令牌 量
        SysAiSessionCompression compression = aiSessionCompressionService.getBySessionId(dialogId);
        if (compression != null && compression.getSysAiCompressionContext() != null
                && !compression.getSysAiCompressionContext().isEmpty()) {
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("role", "system");
            ctx.put("content", "以下是之前对话的摘要上下文：\n" + compression.getSysAiCompressionContext());
            msgList.add(ctx);
            // 仅追加本轮最新的用户消息，保证当前问题被回答
            if (messages != null && !messages.isEmpty()) {
                ChatMessage last = messages.getLast();
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

 // 注入 appKey 与分组 标识 到扩展参数，供用量统计与配额同步使用
        String appKey = resolveAppKey(servletRequest);
        if (appKey != null) {
            Map<String, Object> parameters = request.getParameters();
            if (parameters == null) {
                parameters = new LinkedHashMap<>();
                request.setParameters(parameters);
            }
            parameters.put("_appKey", appKey);
            Integer groupId = resolveGroupId(servletRequest);
            if (groupId != null) {
                parameters.put("_groupId", groupId);
            }
        }

        // 调用 AI 门面进行流式回复，收集完整回复保存为 assistant 消息并更新压缩上下文
        return aiChatFacadeService.stream(request, reply -> {
            if (reply != null && !reply.isEmpty()) {
                aiDialogRecordService.addMessage(dialogId, ChatMessage.assistant(reply));
            }
            // 回复完成后，将历史压缩为「摘要 + 最近 N 轮」写入上下文表
            /**
             * compressand保存上下文。
             * @param dialogId dialogid
             */
            compressAndSaveContext(dialogId, chatModel);
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
     * @param dialogId     会话 标识
     * @param currentModel 本轮对话实际使用的模型，未配置专用压缩模型时摘要按该模型生成
     */
    private void compressAndSaveContext(Long dialogId, String currentModel) {
        try {
            List<ChatMessage> history = aiDialogRecordService.getDialogMessages(dialogId);
            if (history == null || history.isEmpty()) {
                return;
            }
            // 读取压缩配置
            String compressType = getStringSetting("chat_compress_type", "rounds");
            int triggerRounds = parseIntSetting("chat_compress_trigger_rounds", 100);
            int tokenThresholdPercent = parseIntSetting("chat_compress_token_threshold", 80);
            int retainRounds = parseIntSetting("chat_compress_retain_rounds", 6);

            if (triggerRounds <= 0) {
                triggerRounds = 100;
            }
            if (tokenThresholdPercent <= 0) {
                tokenThresholdPercent = 80;
            }
            if (retainRounds <= 0) {
                retainRounds = 6;
            }
            int retainCount = retainRounds * 2;

            // 检查是否需要触发压缩
            boolean shouldCompress = false;

            if ("rounds".equals(compressType) || "both".equals(compressType)) {
                // 按轮数触发
                if (history.size() > triggerRounds * 2) {
                    shouldCompress = true;
                }
            }

            if ("tokens".equals(compressType) || "both".equals(compressType)) {
                // 按上下文阈值触发（估算 token 数 ≈ 字符数 / 4）
                int totalChars = 0;
                for (ChatMessage msg : history) {
                    if (msg.getContent() != null) {
                        totalChars += msg.getContent().length();
                    }
                }
                // 假设模型窗口 128K tokens，估算阈值
                int estimatedTokens = totalChars / 4;
                int maxTokens = 128000; // 默认模型窗口
                double usagePercent = (double) estimatedTokens / maxTokens * 100;
                if (usagePercent > tokenThresholdPercent) {
                    shouldCompress = true;
                }
            }

            // 未达到触发条件时不压缩
            if (!shouldCompress) {
                return;
            }
            // 保留尾部已覆盖全部历史时没有可摘要的头部，强行切片会越界
            if (retainCount >= history.size()) {
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
            
            // 调用 AI 模型生成真正的摘要
            String aiSummary = generateSummaryWithAI(summary.toString(), dialogId, currentModel);
            
            // 拼接：AI 摘要 + 最近 N 轮原始对话
            StringBuilder context = new StringBuilder();
            if (aiSummary != null && !aiSummary.isEmpty()) {
                context.append("[Compressed Context]\n").append(aiSummary);
            } else if (summary.length() > 0) {
                // 降级：如果 AI 摘要失败，用原始截断
                context.append("[Compressed Context]\n")
                        .append(summary.length() > SUMMARY_MAX_LENGTH ? summary.substring(0, SUMMARY_MAX_LENGTH) : summary);
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
            String finalContext = context.length() > COMPRESSED_CONTEXT_MAX_LENGTH
                    ? context.substring(0, COMPRESSED_CONTEXT_MAX_LENGTH)
                    : context.toString();
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

    /**
     * 从全局设置读取字符串配置，未配置时返回默认值。
     *
     * @param field        配置字段名（runtime 分组）
     * @param defaultValue 默认值
     * @return 配置值
     */
    private String getStringSetting(String field, String defaultValue) {
        return sysAiGlobalSettingService.getValue("runtime", field)
                .map(value -> value.trim())
                .filter(value -> !value.isEmpty())
                .orElse(defaultValue);
    }

    /**
     * 解析本轮对话使用的模型。
     *
     * <p>优先取请求参数，其次取全局配置 {@code runtime.default_model}；两者都为空时返回空，
     * 由对话门面自行回落。</p>
     *
     * @param requestedModel 请求携带的模型名，可为空
     * @return 生效的模型名，未配置时为空串
     */
    private String resolveChatModel(String requestedModel) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return requestedModel.trim();
        }
        return getStringSetting("default_model", "");
    }

    /**
     * 调用 AI 模型生成对话摘要。
     * <p>模型取自 {@code runtime.compression_model}，未配置时回退到本轮对话使用的模型；
     * 压缩指令作为系统提示词，历史文本超过 {@link #SUMMARY_INPUT_MAX_LENGTH} 时先截断再送入模型。
     *
     * @param historyText  历史对话文本
     * @param dialogId     会话 ID
     * @param currentModel 本轮对话使用的模型，作为压缩模型未配置时的回退
     * @return 生成的摘要；无可用模型、返回空白或调用异常时返回 null，由调用方降级为原文截断
     */
    private String generateSummaryWithAI(String historyText, Long dialogId, String currentModel) {
        if (historyText == null || historyText.isEmpty()) {
            return null;
        }
        try {
            // 压缩模型优先取专用配置，未配置则沿用本轮对话模型
            String compressionModel = getStringSetting("compression_model", "");
            String summaryModel = !compressionModel.isEmpty() ? compressionModel
                    : (currentModel == null ? "" : currentModel.trim());
            String input = historyText.length() > SUMMARY_INPUT_MAX_LENGTH
                    ? historyText.substring(0, SUMMARY_INPUT_MAX_LENGTH)
                    : historyText;
            AiChatRequest chatRequest = new AiChatRequest();
            chatRequest.setModel(summaryModel);
            chatRequest.setSystemPrompt(SUMMARY_SYSTEM_PROMPT);
            chatRequest.setPrompt(input);
            chatRequest.setStream(false);
            Map<String, Object> result = aiChatFacadeService.call(chatRequest);
            Object error = result == null ? null : result.get("error");
            if (error != null) {
                log.warn("[Compression] 会话 {} 摘要模型 {} 调用失败，降级为原文截断: {}", dialogId, summaryModel, error);
                return null;
            }
            Object text = result == null ? null : result.get("text");
            String summary = text == null ? "" : text.toString().trim();
            if (summary.isEmpty()) {
                log.warn("[Compression] 会话 {} 摘要模型未返回内容，降级为原文截断", dialogId);
                return null;
            }
            log.info("[Compression] 会话 {} AI 摘要完成：模型 {}，历史 {} 字符 → 摘要 {} 字符",
                    dialogId, summaryModel.isEmpty() ? "默认模型" : summaryModel, input.length(), summary.length());
            return summary;
        } catch (Exception e) {
            log.warn("[Compression] AI 摘要生成失败: {}", e.getMessage());
            return null;
        }
    }
}

