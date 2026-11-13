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
import org.springframework.beans.factory.annotation.Autowired;
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

import com.chua.common.support.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

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

    /**
     * aidialogrecord服务
    */
    private final AIDialogRecordService aiDialogRecordService;
    /**
     * aichatfacade服务
    */
    private final AiChatFacadeService aiChatFacadeService;
    /**
     * AI会话compression服务
    */
    private final SysAiSessionCompressionService aiSessionCompressionService;
    /**
     * SYSAI全局设置服务
    */
    private final SysAiGlobalSettingService sysAiGlobalSettingService;
    /**
     * AI 异步任务线程池（压缩任务交由其执行，不阻塞回复输出）
     */
    @Autowired
    protected Executor taskExecutor;

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
     * 压缩开关默认值（自动启用）
     */
    private static final boolean AUTO_COMPRESSION_DEFAULT = true;

    /**
     * 压缩异步默认值（不阻塞回复输出）
     */
    private static final boolean ASYNC_COMPRESSION_DEFAULT = false;

    /**
     * 压缩触发余量百分比默认值（上下文超 80% 时触发）
     */
    private static final int HEADROOM_PERCENT_DEFAULT = 20;

    /**
     * 压缩模式默认值（all / threshold）
     */
    private static final String MODE_DEFAULT = "all";

    /**
     * 压缩模型默认值（空=使用内置默认）
     */
    private static final String MODEL_ID_DEFAULT = "";

    /**
     * 压缩后保留的最近上下文令牌上限默认值
     */
    private static final int RECENT_TOKEN_LIMIT_DEFAULT = 30_000;

    /**
     * 压缩验证默认值（首次压缩结果作为基线融合）
     */
    private static final boolean VALIDATE_DEFAULT = true;

    /**
     * 默认模型上下文窗口大小（tokens）
     */
    private static final int DEFAULT_MODEL_WINDOW = 128_000;

    /**
     * 压缩开关：是否启用自动上下文压缩。
     * <p>关闭时任何历史长度都不触发压缩。</p>
     *
     * @param dialogId 会话标识
     * @param currentModel 本轮对话实际使用的模型，未配置专用压缩模型时摘要按该模型生成
     */
    private void compressAndSaveContext(Long dialogId, String currentModel) {
        CompressionSettings settings;
        try {
            settings = readCompressionSettings();
        } catch (Exception e) {
            log.warn("[AIChat] 读取压缩设置失败，使用默认值: {}", e.getMessage());
            settings = new CompressionSettings(AUTO_COMPRESSION_DEFAULT, ASYNC_COMPRESSION_DEFAULT,
                    HEADROOM_PERCENT_DEFAULT, MODE_DEFAULT, MODEL_ID_DEFAULT,
                    RECENT_TOKEN_LIMIT_DEFAULT, VALIDATE_DEFAULT);
        }
        if (!settings.auto()) {
            return;
        }
        if (settings.async() && taskExecutor != null) {
            // 不阻塞回复输出，压缩交给后台线程池处理；
            // settings 为 lambda 的参数，必须是（有效）最终量，先复制一份
            final CompressionSettings asyncSettings = settings;
            taskExecutor.execute(() -> compress(contextOf(dialogId, currentModel, asyncSettings)));
            return;
        }
        compress(contextOf(dialogId, currentModel, settings));
    }

    /**
     * 压缩上下文承载体。
     *
     * @param dialogId 会话标识
     * @param currentModel 本轮对话实际使用的模型，未配置专用压缩模型时摘要按该模型生成
     * @param settings 压缩设置
     * @return 承载体
     */
    private CompressionContext contextOf(Long dialogId, String currentModel, CompressionSettings settings) {
        return new CompressionContext(dialogId, currentModel, settings,
                aiSessionCompressionService.getBySessionId(dialogId));
    }

    /**
     * 执行压缩。
     *
     * @param ctx 承载体
     */
    private void compress(CompressionContext ctx) {
        Long dialogId = ctx.dialogId;
        String currentModel = ctx.currentModel;
        CompressionSettings settings = ctx.settings;
        try {
            List<ChatMessage> history = aiDialogRecordService.getDialogMessages(dialogId);
            if (history == null || history.isEmpty()) {
                return;
            }
            // 估算当前历史令牌用量，结合余量百分比判断是否触发
            int historyTokens = historyTokens(history);
            // 预算：上下文占用超过 100-headroomPercent% 时触发
            // [P3C A20 豁免]
            int budget = DEFAULT_MODEL_WINDOW * (100 - settings.headroomPercent()) / 100;
            if (historyTokens <= budget) {
                log.debug("[AIChat] 会话 {} 上下文 {} tokens 未达触发阈值 {}，跳过压缩", dialogId, historyTokens, budget);
                return;
            }
            // 保留最近消息的令牌上限：threshold 模式只压缩超出余量的部分，保留整个预算；
            // all 模式保留 fixed recentTokenLimit 条最近消息。
            int retainTokens = "threshold".equalsIgnoreCase(settings.mode()) ? budget : settings.recentTokenLimit();
            // 拆分历史：保留窗口（最近消息）与摘要头（需压缩的历史）
            List<ChatMessage> retained = new ArrayList<>();
            List<ChatMessage> head = new ArrayList<>();
            int retainedTokensTotal = 0;
            for (int i = history.size() - 1; i >= 0; i--) {
                ChatMessage msg = history.get(i);
                int tokens = messageTokens(msg);
                if (retainedTokensTotal + tokens > retainTokens) {
                    head.add(msg);
                } else {
                    retainedTokensTotal += tokens;
                    retained.add(msg);
                }
            }
            if (head.isEmpty()) {
                // 没有可压缩的头部历史
                return;
            }
            // 倒置：头部的消息按从旧到新排列
            Collections.reverse(head);
            // 被摘要覆盖的最后一条消息（分界点）：压缩记录读它来跳过已摘要的历史
            Long fromMessageId = null;
            for (int i = head.size() - 1; i >= 0; i--) {
                if (head.get(i).getId() != null) {
                    fromMessageId = head.get(i).getId();
                    break;
                }
            }
            StringBuilder headText = new StringBuilder();
            for (ChatMessage msg : head) {
                append(headText, msg);
            }
            // 首次压缩或验证未开启时，基线为空
            String baseline = ctx.existing != null ? ctx.existing.getSysAiCompressionBaseline() : null;
            String summary;
            if (settings.validate() && baseline != null && !baseline.isBlank() && !headText.isEmpty()) {
                // 融合基线：把首次压缩结果作为关键信息基线送进摘要模型
                summary = generateSummary(dialogId, buildFusedPrompt(baseline, headText.toString()), currentModel, settings.modelId());
            } else if (!headText.isEmpty()) {
                summary = generateSummary(dialogId, headText.toString(), currentModel, settings.modelId());
            } else {
                summary = null;
            }
            if (summary == null || summary.isBlank()) {
                // 降级：如果 AI 摘要失败，用原始截断
                log.warn("[AIChat] 会话 {} 压缩摘要生成失败，降级为原文截断", dialogId);
                summary = headText.length() > SUMMARY_MAX_LENGTH ? headText.substring(0, SUMMARY_MAX_LENGTH) : headText.toString();
            }
            // 拼接：摘要 + 保留的最近消息
            StringBuilder context = new StringBuilder();
            context.append("[Compressed Context]\n").append(summary);
            for (ChatMessage msg : retained) {
                append(context, msg);
            }
            // 上下文上限，防止无限膨胀
            String finalContext = context.length() > COMPRESSED_CONTEXT_MAX_LENGTH
                    ? context.substring(0, COMPRESSED_CONTEXT_MAX_LENGTH)
                    : context.toString();
            SysAiSessionCompression compression = new SysAiSessionCompression();
            compression.setSysAiSessionId(dialogId);
            compression.setSysAiCompressionMessageCount((long) history.size());
            compression.setSysAiCompressionContext(finalContext);
            // 首次压缩把结果永久存为基线，后续压缩保留基线不变
            String existingBaseline = ctx.existing() != null ? ctx.existing().getSysAiCompressionBaseline() : null;
            compression.setSysAiCompressionBaseline(settings.validate()
                    ? (baseline == null || baseline.isBlank() ? finalContext : existingBaseline)
                    : (baseline == null ? finalContext : baseline));
            if (fromMessageId != null) {
                compression.setSysAiCompressionFromMessageId(fromMessageId.intValue());
            }
            aiSessionCompressionService.saveOrUpdate(compression);
            log.info("[AIChat] 会话 {} 压缩上下文已保存：摘要头 {} 条，保留最近 {} 条（~{} tokens），消息总数 {}，基线{}",
                    dialogId, head.size(), retained.size(), retainedTokensTotal, history.size(),
                    compression.getSysAiCompressionBaseline() == null ? "无" : "有");
        } catch (Exception e) {
            log.warn("[AIChat] 会话 {} 压缩上下文保存失败: {}", dialogId, e.getMessage());
        }
    }

    /**
     * 向文本追加一条消息（角色 + 内容），换行间隔。
     *
     * @param sb 文本
     * @param msg 消息
     */
    private static void append(StringBuilder sb, ChatMessage msg) {
        String role = msg.getRole() != null ? msg.getRole() : "user";
        String content = msg.getContent();
        if (content == null || content.isEmpty()) {
            return;
        }
        sb.append("[").append(role).append("]: ").append(content).append("\n");
    }

    /**
     * 压缩历史消息令牌估算（旧有循环拆线为按条估算）。
     *
     * @param history 历史消息
     * @return 估算令牌数
     */
    private static int historyTokens(List<ChatMessage> history) {
        int tokens = 0;
        for (ChatMessage msg : history) {
            tokens += messageTokens(msg);
        }
        return tokens;
    }

    /**
     * 估算单条消息的令牌数。
     *
     * <p>中日韩字符按 2 token 计（约占另外还有 1 token），其余按 1 token 计。
     * 只用于触发条件判定，不追求精确。</p>
     *
     * @param msg 消息
     * @return 估算令牌数
     */
    private static int messageTokens(ChatMessage msg) {
        String content = msg.getContent();
        if (content == null || content.isEmpty()) {
            return 0;
        }
        int tokens = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            tokens += (c >= 0x1100) ? 2 : 1;
        }
        return tokens;
    }

    /**
     * 融合基线与历史文本：基线（首次压缩结果）作为关键信息锚点。
     *
     * @param baseline 首次压缩结果
     * @param historyText 历史文本
     * @return 融合后的提示文本
     */
    private static String buildFusedPrompt(String baseline, String historyText) {
        return "以下是首次压缩时的会话摘要（关键信息锚点，输出新摘要时必须保留其中的事实）：\n"
                + baseline + "\n\n以下是需要压缩的新增历史文本：\n" + historyText;
    }

    /**
     * 压缩设置承载体。
     *
     * @param auto 是否启用自动上下文压缩
     * @param async 是否异步压缩
     * @param headroomPercent 压缩触发余量百分比
     * @param mode 压缩模式（all / threshold）
     * @param modelId 生成摘要用的压缩模型
     * @param recentTokenLimit 压缩后保留的最近上下文令牌上限
     * @param validate 是否把首次压缩结果作为基线融合进后续摘要
     */
    private record CompressionSettings(boolean auto, boolean async, int headroomPercent, String mode,
                                       String modelId, int recentTokenLimit, boolean validate) {
    }

    /**
     * 压缩上下文承载体。
     *
     * @param dialogId 会话标识
     * @param currentModel 本轮对话实际使用的模型
     * @param settings 压缩设置
     */
    private record CompressionContext(Long dialogId, String currentModel, CompressionSettings settings,
                                        SysAiSessionCompression existing) {
    }

    /**
     * 从 runtime 分组读取压缩设置。
     *
     * @return 压缩设置
     */
    private CompressionSettings readCompressionSettings() {
        return new CompressionSettings(
                getBooleanSetting("auto", AUTO_COMPRESSION_DEFAULT),
                getBooleanSetting("async", ASYNC_COMPRESSION_DEFAULT),
                getIntegerSetting("headroomPercent", HEADROOM_PERCENT_DEFAULT),
                firstNonBlank(getValueSetting("mode"), MODE_DEFAULT),
                firstNonBlank(getValueSetting("modelId"), MODEL_ID_DEFAULT),
                getIntegerSetting("recentTokenLimit", RECENT_TOKEN_LIMIT_DEFAULT),
                getBooleanSetting("validate", VALIDATE_DEFAULT));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) return value;
        }
        return null;
    }

    /**
     * 从全局设置读取字符串配置，未配置时返回默认值。
     *
     * @param field 配置字段名（runtime 分组）
     * @param defaultValue 默认值
     * @return 配置值
     */
    private String getValueSetting(String field) {
        return sysAiGlobalSettingService.getValue("runtime", field).map(String::trim).orElse(null);
    }

    /**
     * 从全局设置读取字符串配置，未配置时返回默认值。
     *
     * @param field 配置字段名（runtime 分组）
     * @param defaultValue 默认值
     * @return 配置值
     */
    private String getStringSetting(String field, String defaultValue) {
        String value = getValueSetting(field);
        return StringUtils.isNotBlank(value) ? value : defaultValue;
    }

    private boolean getBooleanSetting(String field, boolean defaultValue) {
        String value = sysAiGlobalSettingService.getValue("runtime", field).orElse(null);
        if (value == null) return defaultValue;
        return !"false".equalsIgnoreCase(value) && !"0".equalsIgnoreCase(value);
    }

    private int getIntegerSetting(String field, int defaultValue) {
        String value = sysAiGlobalSettingService.getValue("runtime", field).orElse(null);
        if (value == null || value.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    /**
     * 调用 AI 模型生成对话摘要。
     * <p>模型取自 {@code runtime.modelId}，未配置时回退到 {@code runtime.compression_model}，
     * 再回退到本轮对话使用的模型；压缩指令作为系统提示词，历史文本超过指定长度时先截断再送入模型。</p>
     *
     * @param dialogId 会话 ID
     * @param historyText 历史对话文本
     * @param currentModel 本轮对话使用的模型，作为压缩模型未配置时的回退
     * @param modelId 用户配置的压缩模型，优先使用
     * @return 生成的摘要；无可用模型、返回空白或调用异常时返回 null，由调用方降级为原文截断
     */
    private String generateSummary(Long dialogId, String historyText, String currentModel, String modelId) {
        if (historyText == null || historyText.isEmpty()) {
            return null;
        }
        try {
            // 压缩模型优先级：runtime.modelId > runtime.compression_model > 本轮对话模型
            String compressionModel = firstNonBlank(modelId, getStringSetting("compression_model", ""),
                    currentModel == null ? "" : currentModel.trim());
            if (compressionModel == null || compressionModel.isEmpty()) {
                log.warn("[AIChat] 会话 {} 无可用的摘要生成模型，跳过压缩", dialogId);
                return null;
            }
            String input = historyText.length() > SUMMARY_INPUT_MAX_LENGTH
                    ? historyText.substring(0, SUMMARY_INPUT_MAX_LENGTH)
                    : historyText;
            AiChatRequest chatRequest = new AiChatRequest();
            chatRequest.setModel(compressionModel);
            chatRequest.setSystemPrompt(SUMMARY_SYSTEM_PROMPT);
            chatRequest.setPrompt(input);
            chatRequest.setStream(false);
            Map<String, Object> result = aiChatFacadeService.call(chatRequest);
            Object error = result == null ? null : result.get("error");
            if (error != null) {
                log.warn("[Compression] 会话 {} 摘要模型 {} 调用失败，降级为原文截断: {}", dialogId, compressionModel, error);
                return null;
            }
            Object text = result == null ? null : result.get("text");
            String summary = text == null ? "" : text.toString().trim();
            if (summary.isEmpty()) {
                log.warn("[Compression] 会话 {} 摘要模型未返回内容，降级为原文截断", dialogId);
                return null;
            }
            log.info("[Compression] 会话 {} AI 摘要完成：模型 {}，历史 {} 字符 → 摘要 {} 字符",
                    dialogId, compressionModel, input.length(), summary.length());
            return summary;
        } catch (Exception e) {
            log.warn("[Compression] AI 摘要生成失败: {}", e.getMessage());
            return null;
        }
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
}

