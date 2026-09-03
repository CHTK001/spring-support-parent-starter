package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.SysAiConversation;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.manage.store.SysAiConversationMessage;
import com.chua.starter.ai.support.manage.store.SysAiSshHost;
import com.chua.starter.ai.support.manage.store.SysAiSshHostStore;
import com.chua.starter.ai.support.manage.ssh.SshConnectionManager;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 前端对话核心控制器（对接 nomicore 契约）。
 *
 * <p>提供会话 CRUD 与基于 WebSocket 的流式对话能力，替代 Rust 后端的对话接口。
 * 复用 {@link AiChatFacadeService} 完成模型路由与流式推理，通过
 * {@link ApiWebSocketPushService} 将 message.stream / turn.completed 等事件推送给前端。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
public class ApiChatController {

    /**
     * 会话存储
     */
    private final SysAiConversationStore conversationStore;

    /**
     * AI 对话门面服务
     */
    private final AiChatFacadeService aiChatFacadeService;

    /**
     * WS 事件推送服务
     */
    private final ApiWebSocketPushService pushService;

    /**
     * 远程主机（SSH）连接管理服务
     */
    private final SshConnectionManager sshConnectionManager;

    /**
     * 远程主机主机簿存储（校验绑定主机是否存在）
     */
    private final SysAiSshHostStore sshHostStore;

    /**
     * 会话产物（artifacts）内存存储：conversationId → 产物列表（进程内占位实现）
     */
    private final Map<String, List<Map<String, Object>>> artifacts = new ConcurrentHashMap<>();

    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        SysAiConversation c = new SysAiConversation();
        String id = conversationStore.newId();
        c.setConversationId(id);
        c.setType((String) body.getOrDefault("type", "nomi"));
        c.setName((String) body.getOrDefault("name", "新对话"));
        if (body.get("model") instanceof Map) {
            c.setModel(uncheckedCast(body.get("model")));
        }
        if (body.get("extra") instanceof Map) {
            c.setExtra(uncheckedCast(body.get("extra")));
        }
        if (body.get("execution_template_id") != null) {
            c.setExecutionTemplateId(String.valueOf(body.get("execution_template_id")));
        }
        long now = System.currentTimeMillis();
        c.setCreatedAt(now);
        c.setModifiedAt(now);
        conversationStore.create(c);
        // 绑定远程主机（extra.ssh_host_id）的会话：校验主机存在并异步拨号，
        // 使会话 header 上的「远程主机」状态药丸真实可见。
        if (c.getExtra() != null && c.getExtra().get("ssh_host_id") != null) {
            dialBoundSshHost(String.valueOf(c.getExtra().get("ssh_host_id")), id);
        }
        pushService.push("conversation.listChanged", Map.of("conversation_id", id, "action", "created"));
        return ApiResult.ok(toResponse(c));
    }

    /**
     * 为绑定远程主机的会话发起异步拨号（主机不存在时仅告警，不阻塞会话创建）。
     *
     * @param sshHostId       主机 ID
     * @param conversationId  会话 ID
     */
    private void dialBoundSshHost(String sshHostId, String conversationId) {
        try {
            SysAiSshHost host = sshHostStore.get(sshHostId);
            sshConnectionManager.dial(host, conversationId);
        } catch (Exception e) {
            log.warn("[Chat] 绑定远程主机拨号失败: ssh_host_id={} err={}", sshHostId, e.getMessage());
        }
    }

    @GetMapping
    public ApiResult<Map<String, Object>> list(@RequestParam(required = false, defaultValue = "1") int page,
                                               @RequestParam(required = false, defaultValue = "50") int page_size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysAiConversation> paging =
                conversationStore.page(page, page_size);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", paging.getRecords().stream().map(this::toResponse).toList());
        result.put("total", paging.getTotal());
        result.put("page", paging.getCurrent());
        result.put("page_size", paging.getSize());
        result.put("has_more", paging.getCurrent() * paging.getSize() < paging.getTotal());
        return ApiResult.ok(result);
    }

    @GetMapping("/{conversationId}")
    public ApiResult<Map<String, Object>> get(@PathVariable String conversationId) {
        SysAiConversation c = conversationStore.get(conversationId);
        return ApiResult.ok(toResponse(c));
    }

    @PatchMapping("/{conversationId}")
    public ApiResult<Boolean> patch(@PathVariable String conversationId, @RequestBody Map<String, Object> body) {
        SysAiConversation patch = new SysAiConversation();
        if (body.containsKey("name")) {
            patch.setName((String) body.get("name"));
        }
        if (body.containsKey("pinned")) {
            patch.setPinned(Boolean.TRUE.equals(body.get("pinned")));
        }
        if (body.containsKey("pinned_at") && body.get("pinned_at") != null) {
            patch.setPinnedAt(((Number) body.get("pinned_at")).longValue());
        }
        if (body.containsKey("model") && body.get("model") instanceof Map) {
            patch.setModel(uncheckedCast(body.get("model")));
        }
        if (body.containsKey("execution_template_id")) {
            patch.setExecutionTemplateId(String.valueOf(body.get("execution_template_id")));
        }
        if (body.containsKey("extra") && body.get("extra") instanceof Map) {
            patch.setExtra(uncheckedCast(body.get("extra")));
        }
        conversationStore.patch(conversationId, patch);
        pushService.push("conversation.listChanged", Map.of("conversation_id", conversationId, "action", "updated"));
        return ApiResult.ok(true);
    }

    @DeleteMapping("/{conversationId}")
    public ApiResult<Void> delete(@PathVariable String conversationId) {
        conversationStore.delete(conversationId);
        pushService.push("conversation.listChanged", Map.of("conversation_id", conversationId, "action", "deleted"));
        return ApiResult.ok();
    }

    @PostMapping("/{conversationId}/messages")
    public ApiResult<Map<String, Object>> sendMessage(@PathVariable String conversationId,
                                                       @RequestBody Map<String, Object> body) {
        SysAiConversation conversation = conversationStore.get(conversationId);
        String input = (String) body.get("content");
        if (input == null || input.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "消息内容不能为空");
        }

        // 1. 保存用户消息
        SysAiConversationMessage userMsg = newSysAiConversationMessage(conversationId, "text", input, "user", "right");
        conversationStore.addMessage(conversationId, userMsg);

        // 2. 推送 userCreated
        pushService.push("message.userCreated", Map.of(
                "conversation_id", conversationId,
                "msg_id", userMsg.getMessageId(),
                "content", input,
                "position", "right",
                "status", "finish",
                "created_at", userMsg.getCreatedAt()
        ));

        // 3. 生成 turn 与 assistant 消息 id
        String turnId = conversationStore.newId();
        String assistantMsgId = conversationStore.newId();

        // 4. 推送 turn.started
        pushService.push("turn.started", Map.of(
                "conversation_id", conversationId,
                "turn_id", turnId,
                "status", "running",
                "state", "ai_generating",
                "can_send_message", false,
                "runtime", Map.of(
                        "state", "ai_generating",
                        "can_send_message", false,
                        "has_runtime", false,
                        "is_processing", true,
                        "pending_confirmations", 0,
                        "active_turn_id", turnId
                )
        ));

        // 5. 构建请求并异步流式调用（回调由 ChatClient 驱动，HTTP 立即返回）
        AiChatRequest request = buildRequest(conversation, input);
        String modelName = request.getModel();

        aiChatFacadeService.stream(request, (chunk) -> {
            if (chunk != null && !chunk.isEmpty()) {
                pushService.push("message.stream", Map.of(
                        "conversation_id", conversationId,
                        "msg_id", assistantMsgId,
                        "turn_id", turnId,
                        "type", "text",
                        "data", chunk,
                        "status", "work"
                ));
            }
        }, (fullReply) -> {
            // 6. 保存 assistant 消息
            SysAiConversationMessage assistantMsg = newSysAiConversationMessage(conversationId, "text",
                    fullReply != null ? fullReply : "", "assistant", "left");
            assistantMsg.setMessageId(assistantMsgId);
            assistantMsg.setMsgId(assistantMsgId);
            conversationStore.addMessage(conversationId, assistantMsg);

            // 7. 推送 turn.completed（runtime 用可空 LinkedHashMap，Map.of 不允许 null value）
            java.util.Map<String, Object> runtimeMap = new LinkedHashMap<>();
            runtimeMap.put("state", "ai_waiting_input");
            runtimeMap.put("can_send_message", true);
            runtimeMap.put("has_runtime", false);
            runtimeMap.put("is_processing", false);
            runtimeMap.put("pending_confirmations", 0);
            runtimeMap.put("active_turn_id", (Object) null);
            pushService.push("turn.completed", Map.of(
                    "conversation_id", conversationId,
                    "turn_id", turnId,
                    "status", "finished",
                    "state", "ai_waiting_input",
                    "can_send_message", true,
                    "runtime", runtimeMap,
                    "workspace", "",
                    "model", Map.of("platform", "", "name", modelName != null ? modelName : "",
                            "use_model", modelName != null ? modelName : ""),
                    "last_message", Map.of(
                            "message_id", assistantMsgId,
                            "type", "text",
                            "content", fullReply != null ? fullReply : "",
                            "status", "finish"
                    )
            ));
            pushService.push("conversation.listChanged",
                    Map.of("conversation_id", conversationId, "action", "updated"));
        });

        // 8. 返回发送结果（前端据此校正乐观气泡）
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("msg_id", userMsg.getMessageId());
        result.put("replayed", false);
        result.put("completed", false);
        return ApiResult.ok(result);
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResult<Map<String, Object>> listMessages(@PathVariable String conversationId,
                                                       @RequestParam(required = false, defaultValue = "1") int page,
                                                       @RequestParam(required = false, defaultValue = "50") int page_size,
                                                       @RequestParam(required = false) String order,
                                                       @RequestParam(required = false) String content_mode,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false) String day) {
        conversationStore.get(conversationId);
        List<SysAiConversationMessage> msgs = conversationStore.listMessages(conversationId, page, page_size);
        Map<String, Object> pageResult = new LinkedHashMap<>();
        pageResult.put("items", msgs.stream().map(this::toMessageResponse).toList());
        pageResult.put("total", msgs.size());
        pageResult.put("has_more", false);
        return ApiResult.ok(pageResult);
    }

    @GetMapping("/{conversationId}/messages/{messageId}")
    public ApiResult<Map<String, Object>> getMessage(@PathVariable String conversationId,
                                                    @PathVariable String messageId) {
        SysAiConversationMessage msg = conversationStore.getMessage(conversationId, messageId);
        return ApiResult.ok(toMessageResponse(msg));
    }

    @PostMapping("/clone")
    public ApiResult<Map<String, Object>> clone(@RequestBody Map<String, Object> body) {
        Object convObj = body.get("conversation");
        if (!(convObj instanceof Map)) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "缺少 conversation 字段");
        }
        Map<String, Object> source = uncheckedCast(convObj);
        String id = conversationStore.newId();
        SysAiConversation c = new SysAiConversation();
        c.setConversationId(id);
        c.setType((String) source.getOrDefault("type", "nomi"));
        c.setName((String) source.getOrDefault("name", "新对话"));
        if (source.get("model") instanceof Map) {
            c.setModel(uncheckedCast(source.get("model")));
        }
        if (source.get("extra") instanceof Map) {
            c.setExtra(uncheckedCast(source.get("extra")));
        }
        if (source.get("execution_template_id") != null) {
            c.setExecutionTemplateId(String.valueOf(source.get("execution_template_id")));
        }
        long now = System.currentTimeMillis();
        c.setCreatedAt(now);
        c.setModifiedAt(now);
        conversationStore.create(c);
        pushService.push("conversation.listChanged", Map.of("conversation_id", id, "action", "created"));
        return ApiResult.ok(toResponse(c));
    }

    @PostMapping("/{conversationId}/reset")
    public ApiResult<Void> reset(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        clearMessagesInternal(conversationId);
        pushService.push("conversation.listChanged", Map.of("conversation_id", conversationId, "action", "updated"));
        return ApiResult.ok();
    }

    @PostMapping("/{conversationId}/warmup")
    public ApiResult<Void> warmup(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        return ApiResult.ok();
    }

    @PostMapping("/{conversationId}/cancel")
    public ApiResult<Void> cancel(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        String turnId = conversationStore.newId();
        java.util.Map<String, Object> runtimeMap = new LinkedHashMap<>();
        runtimeMap.put("state", "ai_waiting_input");
        runtimeMap.put("can_send_message", true);
        runtimeMap.put("has_runtime", false);
        runtimeMap.put("is_processing", false);
        runtimeMap.put("pending_confirmations", 0);
        runtimeMap.put("active_turn_id", (Object) null);
        pushService.push("turn.completed", Map.of(
                "conversation_id", conversationId,
                "turn_id", turnId,
                "status", "finished",
                "state", "ai_waiting_input",
                "can_send_message", true,
                "runtime", runtimeMap
        ));
        return ApiResult.ok();
    }

    @PostMapping("/{conversationId}/clear-context")
    public ApiResult<Void> clearContext(@PathVariable String conversationId) {
        SysAiConversation c = conversationStore.get(conversationId);
        if (c.getExtra() != null) {
            c.getExtra().remove("summon");
        }
        Map<String, Object> runtime = c.getRuntime();
        if (runtime != null) {
            runtime.remove("active_turn_id");
        }
        c.setRuntime(runtime);
        c.setModifiedAt(System.currentTimeMillis());
        pushService.push("conversation.listChanged", Map.of("conversation_id", conversationId, "action", "updated"));
        return ApiResult.ok();
    }

    @PostMapping("/{conversationId}/clear-messages")
    public ApiResult<Boolean> clearMessages(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        clearMessagesInternal(conversationId);
        pushService.push("conversation.listChanged", Map.of("conversation_id", conversationId, "action", "updated"));
        return ApiResult.ok(true);
    }

    @PutMapping("/{conversationId}/summon")
    public ApiResult<Map<String, Object>> setSummon(@PathVariable String conversationId,
                                                    @RequestBody Map<String, Object> body) {
        SysAiConversation c = conversationStore.get(conversationId);
        Map<String, Object> extra = c.getExtra();
        if (extra == null) {
            extra = new LinkedHashMap<>();
            c.setExtra(extra);
        }
        Map<String, Object> summon = new LinkedHashMap<>();
        summon.put("companion_id", body.get("companion_id"));
        summon.put("memory_ids", body.getOrDefault("memory_ids", new ArrayList<>()));
        summon.put("skill_exclusions", body.getOrDefault("skill_exclusions", new ArrayList<>()));
        summon.put("summoned_at", System.currentTimeMillis());
        extra.put("summon", summon);
        c.setModifiedAt(System.currentTimeMillis());
        pushService.push("conversation.listChanged", Map.of("conversation_id", conversationId, "action", "updated"));
        return ApiResult.ok(summon);
    }

    @DeleteMapping("/{conversationId}/summon")
    public ApiResult<Void> clearSummon(@PathVariable String conversationId) {
        SysAiConversation c = conversationStore.get(conversationId);
        if (c.getExtra() != null) {
            c.getExtra().remove("summon");
        }
        c.setModifiedAt(System.currentTimeMillis());
        return ApiResult.ok();
    }

    @GetMapping("/active-count")
    public ApiResult<Map<String, Object>> activeCount() {
        List<SysAiConversation> all = conversationStore.list(Integer.MAX_VALUE);
        long count = all.stream()
                .filter(c -> {
                    Map<String, Object> rt = c.getRuntime();
                    return rt != null && rt.get("active_turn_id") != null;
                })
                .count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", count);
        return ApiResult.ok(result);
    }

    @GetMapping("/{conversationId}/associated")
    public ApiResult<List<Object>> getAssociated(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        return ApiResult.ok(new ArrayList<>());
    }

    @GetMapping("/{conversationId}/slash-commands")
    public ApiResult<List<Map<String, Object>>> getSlashCommands(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        List<Map<String, Object>> commands = new ArrayList<>();
        Map<String, Object> help = new LinkedHashMap<>();
        help.put("command", "/help");
        help.put("description", "帮助");
        commands.add(help);
        return ApiResult.ok(commands);
    }

    @PostMapping("/{conversationId}/side-question")
    public ApiResult<Map<String, Object>> sideQuestion(@PathVariable String conversationId,
                                                       @RequestBody Map<String, Object> body) {
        conversationStore.get(conversationId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "noAnswer");
        return ApiResult.ok(result);
    }

    @GetMapping("/{conversationId}/artifacts")
    public ApiResult<List<Map<String, Object>>> listArtifacts(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        List<Map<String, Object>> list = artifacts.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>());
        return ApiResult.ok(new ArrayList<>(list));
    }

    @PatchMapping("/{conversationId}/artifacts/{conversationArtifactId}")
    public ApiResult<Map<String, Object>> patchArtifact(@PathVariable String conversationId,
                                                        @PathVariable String conversationArtifactId,
                                                        @RequestBody Map<String, Object> body) {
        return ApiResult.ok(updateArtifactInternal(conversationId, conversationArtifactId, body));
    }

    @PutMapping("/{conversationId}/artifacts/{conversationArtifactId}")
    public ApiResult<Map<String, Object>> putArtifact(@PathVariable String conversationId,
                                                      @PathVariable String conversationArtifactId,
                                                      @RequestBody Map<String, Object> body) {
        return ApiResult.ok(updateArtifactInternal(conversationId, conversationArtifactId, body));
    }

    @GetMapping("/{conversationId}/workspace")
    public ApiResult<List<Map<String, Object>>> getWorkspace(@PathVariable String conversationId,
                                                             @RequestParam(required = false) String path,
                                                             @RequestParam(required = false) String search) {
        conversationStore.get(conversationId);
        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, Object> placeholder = new LinkedHashMap<>();
        placeholder.put("name", "workspace");
        placeholder.put("type", "directory");
        entries.add(placeholder);
        return ApiResult.ok(entries);
    }

    @GetMapping("/{conversationId}/mode")
    public ApiResult<Map<String, Object>> getMode(@PathVariable String conversationId) {
        SysAiConversation c = conversationStore.get(conversationId);
        Map<String, Object> extra = c.getExtra();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", extra != null ? extra.get("mode") : null);
        return ApiResult.ok(result);
    }

    @PutMapping("/{conversationId}/mode")
    public ApiResult<Map<String, Object>> setMode(@PathVariable String conversationId,
                                                  @RequestBody Map<String, Object> body) {
        SysAiConversation c = conversationStore.get(conversationId);
        Map<String, Object> extra = c.getExtra();
        if (extra == null) {
            extra = new LinkedHashMap<>();
            c.setExtra(extra);
        }
        extra.put("mode", body.get("mode"));
        c.setModifiedAt(System.currentTimeMillis());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", body.get("mode"));
        return ApiResult.ok(result);
    }

    @GetMapping("/{conversationId}/approvals/check")
    public ApiResult<Map<String, Object>> checkApproval(@PathVariable String conversationId,
                                                        @RequestParam String action,
                                                        @RequestParam(required = false) String command_type) {
        conversationStore.get(conversationId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approved", true);
        return ApiResult.ok(result);
    }

    @GetMapping("/{conversationId}/confirmations")
    public ApiResult<List<Object>> listConfirmations(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        return ApiResult.ok(new ArrayList<>());
    }

    @PostMapping("/{conversationId}/confirmations/{callId}/confirm")
    public ApiResult<Void> confirm(@PathVariable String conversationId,
                                   @PathVariable String callId,
                                   @RequestBody Map<String, Object> body) {
        conversationStore.get(conversationId);
        return ApiResult.ok();
    }

    @PostMapping("/{conversationId}/steer")
    public ApiResult<Map<String, Object>> steer(@PathVariable String conversationId,
                                                @RequestBody Map<String, Object> body) {
        return doStreamTurn(conversationId, (String) body.get("content"));
    }

    @PostMapping("/{conversationId}/messages/{messageId}/edit-resubmit")
    public ApiResult<Map<String, Object>> editResubmit(@PathVariable String conversationId,
                                                       @PathVariable String messageId,
                                                       @RequestBody Map<String, Object> body) {
        conversationStore.get(conversationId);
        String input = (String) body.get("content");
        if (input == null || input.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "消息内容不能为空");
        }
        conversationStore.truncateAfter(conversationId, messageId);
        return doStreamTurn(conversationId, input);
    }

    @PostMapping("/{conversationId}/messages/{messageId}/knowledge-writeback/retry")
    public ApiResult<Void> retryKnowledgeWriteback(@PathVariable String conversationId,
                                                   @PathVariable String messageId,
                                                   @RequestBody Map<String, Object> body) {
        conversationStore.get(conversationId);
        conversationStore.getMessage(conversationId, messageId);
        return ApiResult.ok();
    }

    private ApiResult<Map<String, Object>> doStreamTurn(String conversationId, String input) {
        if (input == null || input.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "消息内容不能为空");
        }

        // 1. 保存用户消息
        SysAiConversationMessage userMsg = newSysAiConversationMessage(conversationId, "text", input, "user", "right");
        conversationStore.addMessage(conversationId, userMsg);

        // 生成 turn 与 assistant 消息 id
        String turnId = conversationStore.newId();
        String assistantMsgId = conversationStore.newId();

        // 2. 推送 userCreated
        pushService.push("message.userCreated", Map.of(
                "conversation_id", conversationId,
                "msg_id", userMsg.getMessageId(),
                "content", input,
                "position", "right",
                "status", "finish",
                "created_at", userMsg.getCreatedAt()
        ));

        // 3. 推送 turn.started
        pushService.push("turn.started", Map.of(
                "conversation_id", conversationId,
                "turn_id", turnId,
                "status", "running",
                "state", "ai_generating",
                "can_send_message", false,
                "runtime", Map.of(
                        "state", "ai_generating",
                        "can_send_message", false,
                        "has_runtime", false,
                        "is_processing", true,
                        "pending_confirmations", 0,
                        "active_turn_id", turnId
                )
        ));

        // 4. 构建请求并异步流式调用
        SysAiConversation conversation = conversationStore.get(conversationId);
        AiChatRequest request = buildRequest(conversation, input);
        String modelName = request.getModel();

        aiChatFacadeService.stream(request, (chunk) -> {
            if (chunk != null && !chunk.isEmpty()) {
                pushService.push("message.stream", Map.of(
                        "conversation_id", conversationId,
                        "msg_id", assistantMsgId,
                        "turn_id", turnId,
                        "type", "text",
                        "data", chunk,
                        "status", "work"
                ));
            }
        }, (fullReply) -> {
            // 5. 保存 assistant 消息
            SysAiConversationMessage assistantMsg = newSysAiConversationMessage(conversationId, "text",
                    fullReply != null ? fullReply : "", "assistant", "left");
            assistantMsg.setMessageId(assistantMsgId);
            assistantMsg.setMsgId(assistantMsgId);
            conversationStore.addMessage(conversationId, assistantMsg);

            // 6. 推送 turn.completed
            java.util.Map<String, Object> runtimeMap = new LinkedHashMap<>();
            runtimeMap.put("state", "ai_waiting_input");
            runtimeMap.put("can_send_message", true);
            runtimeMap.put("has_runtime", false);
            runtimeMap.put("is_processing", false);
            runtimeMap.put("pending_confirmations", 0);
            runtimeMap.put("active_turn_id", (Object) null);
            pushService.push("turn.completed", Map.of(
                    "conversation_id", conversationId,
                    "turn_id", turnId,
                    "status", "finished",
                    "state", "ai_waiting_input",
                    "can_send_message", true,
                    "runtime", runtimeMap,
                    "workspace", "",
                    "model", Map.of("platform", "", "name", modelName != null ? modelName : "",
                            "use_model", modelName != null ? modelName : ""),
                    "last_message", Map.of(
                            "message_id", assistantMsgId,
                            "type", "text",
                            "content", fullReply != null ? fullReply : "",
                            "status", "finish"
                    )
            ));
            pushService.push("conversation.listChanged",
                    Map.of("conversation_id", conversationId, "action", "updated"));
        });

        // 7. 返回发送结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("msg_id", userMsg.getMessageId());
        result.put("replayed", false);
        result.put("completed", false);
        return ApiResult.ok(result);
    }

    private Map<String, Object> updateArtifactInternal(String conversationId, String artifactId, Map<String, Object> body) {
        conversationStore.get(conversationId);
        List<Map<String, Object>> list = artifacts.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>());
        Map<String, Object> target = list.stream()
                .filter(a -> artifactId.equals(String.valueOf(a.get("conversation_artifact_id"))))
                .findFirst()
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "产物不存在: " + artifactId));
        if (body.containsKey("status")) {
            target.put("status", body.get("status"));
        }
        return target;
    }

    private void clearMessagesInternal(String conversationId) {
        conversationStore.clearMessages(conversationId);
    }

    private SysAiConversationMessage newSysAiConversationMessage(String conversationId, String type, String content, String role, String position) {
        SysAiConversationMessage m = new SysAiConversationMessage();
        String id = conversationStore.newId();
        m.setMessageId(id);
        m.setMsgId(id);
        m.setConversationId(conversationId);
        m.setType(type);
        m.setContent(content);
        m.setRole(role);
        m.setPosition(position);
        m.setStatus("finish");
        m.setHidden(false);
        m.setCreatedAt(System.currentTimeMillis());
        return m;
    }

    private AiChatRequest buildRequest(SysAiConversation conversation, String userInput) {
        AiChatRequest request = new AiChatRequest();
        String modelName = null;
        if (conversation.getModel() != null && conversation.getModel().get("model") != null) {
            modelName = String.valueOf(conversation.getModel().get("model"));
        }
        request.setModel(modelName);
        request.setStream(true);
        request.setPrompt(userInput);

        List<Map<String, Object>> msgList = new ArrayList<>();
        if (conversation.getExtra() != null && conversation.getExtra().get("system_prompt") != null) {
            String systemPrompt = String.valueOf(conversation.getExtra().get("system_prompt"));
            if (!systemPrompt.isBlank()) {
                Map<String, Object> sys = new LinkedHashMap<>();
                sys.put("role", "system");
                sys.put("content", systemPrompt);
                msgList.add(sys);
            }
        }
        for (SysAiConversationMessage m : conversationStore.allMessages(conversation.getConversationId())) {
            if (!"text".equals(m.getType()) || m.getContent() == null) {
                continue;
            }
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("role", m.getRole() != null ? m.getRole() : "user");
            mm.put("content", m.getContent().toString());
            msgList.add(mm);
        }
        if (!msgList.isEmpty()) {
            request.setMessages(msgList);
        }
        return request;
    }

    private Map<String, Object> toResponse(SysAiConversation c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("conversation_id", c.getConversationId());
        map.put("type", c.getType());
        map.put("name", c.getName());
        map.put("model", c.getModel());
        map.put("extra", c.getExtra());
        map.put("pinned", c.isPinned());
        map.put("pinned_at", c.getPinnedAt());
        map.put("created_at", c.getCreatedAt());
        map.put("modified_at", c.getModifiedAt());
        map.put("execution_template_id", c.getExecutionTemplateId());
        return map;
    }

    private Map<String, Object> toMessageResponse(SysAiConversationMessage m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("message_id", m.getMessageId());
        map.put("conversation_id", m.getConversationId());
        map.put("msg_id", m.getMsgId());
        map.put("type", m.getType());
        map.put("content", m.getContent());
        map.put("position", m.getPosition());
        map.put("status", m.getStatus());
        map.put("hidden", m.isHidden());
        map.put("created_at", m.getCreatedAt());
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> uncheckedCast(Object obj) {
        return (Map<String, Object>) obj;
    }
}

/**
 * 消息全局检索控制器（对接 /api/messages 契约，独立于会话命名空间）。
 *
 * <p>前端 {@code ipcBridge} 的 {@code /api/messages/search} 不在 {@code /api/conversations}
 * 之下，故以独立 {@code @RestController} 承载，复用同一套 {@link ApiResult} 信封。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/messages")
class ApiMessageSearchController {

    /**
     * 全局消息检索（占位实现，返回空结果集）。
     *
     * @param keyword   关键字
     * @param page      页号
     * @param page_size 每页条数
     * @return 分页检索结果
     */
    @GetMapping("/search")
    public ApiResult<Map<String, Object>> search(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false, defaultValue = "1") int page,
                                                 @RequestParam(required = false, defaultValue = "20") int page_size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", new ArrayList<>());
        result.put("total", 0);
        result.put("has_more", false);
        return ApiResult.ok(result);
    }
}

