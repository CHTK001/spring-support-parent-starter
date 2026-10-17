package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.model.AiMemoryRecallDTO;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.SysAiConversation;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.manage.store.SysAiConversationMessage;
import com.chua.starter.ai.support.manage.store.SysAiMemoryStore;
import com.chua.starter.ai.support.manage.store.SysAiSshHost;
import com.chua.starter.ai.support.manage.store.SysAiSshHostStore;
import com.chua.starter.ai.support.manage.ssh.SshConnectionManager;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
import com.chua.starter.ai.support.service.AiChatFacadeService;
import com.chua.starter.ai.support.service.SysAiMemoryService;
import com.chua.starter.ai.support.util.AiUserContext;
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
import java.util.concurrent.CompletableFuture;

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
@ApiReturnFormatIgnore
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
     * 记忆体服务：每轮对话前召回并注入长期记忆，回复后从轮次中抽取新记忆
     */
    private final SysAiMemoryService memoryService;

    /**
     * 记忆体存储（会话删除时连带清理该会话的会话级记忆）
     */
    private final SysAiMemoryStore memoryStore;

    @PostMapping
    /**
     * 创建。
     * @param body 主体
     * @return 创建的结果
     */
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
 // 绑定远程主机（extra.ssh_主机_标识）的会话：校验主机存在并异步拨号，
 // 使会话 头部 上的「远程主机」状态药丸真实可见。
        if (c.getExtra() != null && c.getExtra().get("ssh_host_id") != null) {
            /**
             * dialboundssh主机。
             * @param String.valueOf(c.getExtra( 字符串.值的(C.获取extra(
             */
            dialBoundSshHost(String.valueOf(c.getExtra().get("ssh_host_id")), id);
        }
        pushService.push("conversation.listChanged", Map.of("conversation_id", id, "action", "created"));
        return ApiResult.ok(toResponse(c));
    }

    /**
     * 为绑定远程主机的会话发起异步拨号（主机不存在时仅告警，不阻塞会话创建）。
     *
     * @param sshHostId       主机 标识
     * @param conversationId  会话 标识
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
    /**
     * 列表。
     * @param "1" "1"
     * @return 列表的结果
     */
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
    /**
     * 获取。
     * @param conversationId conversationid
     * @return 获取的结果
     */
    public ApiResult<Map<String, Object>> get(@PathVariable String conversationId) {
        SysAiConversation c = conversationStore.get(conversationId);
        return ApiResult.ok(toResponse(c));
    }

    @PatchMapping("/{conversationId}")
    /**
     * patch。
     * @param conversationId conversationid
     * @param body 主体
     * @return patch的结果
     */
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
    /**
     * 删除。
     * @param conversationId conversationid
     * @return 删除的结果
     */
    public ApiResult<Void> delete(@PathVariable String conversationId) {
        conversationStore.delete(conversationId);
        try {
 // 会话级记忆随会话消失，全局记忆不动
            memoryStore.deleteByConversation(conversationId);
        } catch (Exception e) {
            log.warn("[Chat] 会话级记忆清理失败 conversation={}: {}", conversationId, e.getMessage());
        }
        pushService.push("conversation.listChanged", Map.of("conversation_id", conversationId, "action", "deleted"));
        return ApiResult.ok();
    }

    @PostMapping("/{conversationId}/messages")
    public ApiResult<Map<String, Object>> sendMessage(@PathVariable String conversationId,
                                                       @RequestBody Map<String, Object> body) {
        SysAiConversation conversation = conversationStore.get(conversationId);
        String input = (String) body.get("content");
        if (input == null || input.isBlank()) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param "消息内容不能为空" "消息内容不能为空"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "消息内容不能为空");
        }

        // 1. 保存用户消息
        SysAiConversationMessage userMsg = newSysAiConversationMessage(conversationId, "text", input, "user", "right");
        conversationStore.addMessage(conversationId, userMsg);

 // 2. 推送 用户创建
        pushService.push("message.userCreated", Map.of(
                "conversation_id", conversationId,
                "msg_id", userMsg.getMessageId(),
                "content", input,
                "position", "right",
                "status", "finish",
                "created_at", userMsg.getCreatedAt()
        ));

 // 3. 生成 turn 与 assistant 消息 标识
        String turnId = conversationStore.newId();
        String assistantMsgId = conversationStore.newId();
        conversationStore.patchRuntime(conversationId, "active_turn_id", turnId);

 // 4. 推送 turn.启动
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

 // 5. 构建请求并异步流式调用（回调由 对话客户端 驱动，HTTP 立即返回）
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
            if (fullReply == null || fullReply.isBlank()) {
                pushTurnError(conversationId, turnId, assistantMsgId, modelName,
                        new IllegalStateException("模型返回空回复"));
                return;
            }
            // 6. 保存 assistant 消息
            SysAiConversationMessage assistantMsg = newSysAiConversationMessage(conversationId, "text",
                    fullReply, "assistant", "left");
            assistantMsg.setMessageId(assistantMsgId);
            assistantMsg.setMsgId(assistantMsgId);
            conversationStore.addMessage(conversationId, assistantMsg);
            rememberTurn(conversation, input, fullReply, modelName);
            conversationStore.patchRuntime(conversationId, "active_turn_id", null);

 // 7. 推送 turn.完成（runtime 用可空 链接哈希映射，映射.的 不允许 空 值）
            java.util.Map<String, Object> runtimeMap = new LinkedHashMap<>();
            runtimeMap.put("state", "ai_waiting_input");
            runtimeMap.put("can_send_message", true);
            runtimeMap.put("has_runtime", false);
            runtimeMap.put("is_processing", false);
            runtimeMap.put("pending_confirmations", 0);
            Map<String, Object> completed = new LinkedHashMap<>();
            completed.put("conversation_id", conversationId);
            completed.put("turn_id", turnId);
            completed.put("status", "finished");
            completed.put("state", "ai_waiting_input");
            completed.put("can_send_message", true);
            completed.put("runtime", runtimeMap);
            if (modelName != null && !modelName.isBlank()) {
                completed.put("model", Map.of("name", modelName, "use_model", modelName));
            }
            completed.put("last_message", Map.of(
                    "message_id", assistantMsgId,
                    "type", "text",
                    "content", fullReply,
                    "status", "finish"
            ));
            pushService.push("turn.completed", completed);
            pushService.push("conversation.listChanged",
                    Map.of("conversation_id", conversationId, "action", "updated"));
        }, error -> pushTurnError(conversationId, turnId, assistantMsgId, modelName, error));

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
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysAiConversationMessage> paging =
                conversationStore.pageMessages(conversationId, page, page_size);
        Map<String, Object> pageResult = new LinkedHashMap<>();
        pageResult.put("items", paging.getRecords().stream().map(this::toMessageResponse).toList());
        pageResult.put("total", paging.getTotal());
        pageResult.put("page", paging.getCurrent());
        pageResult.put("page_size", paging.getSize());
        pageResult.put("has_more", paging.getCurrent() * paging.getSize() < paging.getTotal());
        return ApiResult.ok(pageResult);
    }

    @GetMapping("/{conversationId}/messages/{messageId}")
    public ApiResult<Map<String, Object>> getMessage(@PathVariable String conversationId,
                                                    @PathVariable String messageId) {
        SysAiConversationMessage msg = conversationStore.getMessage(conversationId, messageId);
        return ApiResult.ok(toMessageResponse(msg));
    }

    @PostMapping("/clone")
    /**
     * clone。
     * @param body 主体
     * @return clone的结果
     */
    public ApiResult<Map<String, Object>> clone(@RequestBody Map<String, Object> body) {
        Object convObj = body.get("conversation");
        if (!(convObj instanceof Map)) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param 字段" 字段"
             * @return api异常的结果
             */
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
    /**
     * reset。
     * @param conversationId conversationid
     * @return reset的结果
     */
    public ApiResult<Void> reset(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        /**
         * clear消息内部。
         * @param conversationId conversationid
         */
        clearMessagesInternal(conversationId);
        pushService.push("conversation.listChanged", Map.of("conversation_id", conversationId, "action", "updated"));
        return ApiResult.ok();
    }

    @PostMapping("/{conversationId}/warmup")
    /**
     * warmup。
     * @param conversationId conversationid
     * @return warmup的结果
     */
    public ApiResult<Void> warmup(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前对话服务未实现模型预热");
    }

    @PostMapping("/{conversationId}/cancel")
    /**
     * cancel。
     * @param conversationId conversationid
     * @return cancel的结果
     */
    public ApiResult<Void> cancel(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前流式对话服务未实现任务取消");
    }

    @PostMapping("/{conversationId}/clear-context")
    /**
     * clear上下文。
     * @param conversationId conversationid
     * @return clear上下文的结果
     */
    public ApiResult<Void> clearContext(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        conversationStore.patchExtra(conversationId, "summon", null);
        conversationStore.patchRuntime(conversationId, "active_turn_id", null);
        pushService.push("conversation.listChanged", Map.of("conversation_id", conversationId, "action", "updated"));
        return ApiResult.ok();
    }

    @PostMapping("/{conversationId}/clear-messages")
    /**
     * clear消息。
     * @param conversationId conversationid
     * @return clear消息的结果
     */
    public ApiResult<Boolean> clearMessages(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        /**
         * clear消息内部。
         * @param conversationId conversationid
         */
        clearMessagesInternal(conversationId);
        pushService.push("conversation.listChanged", Map.of("conversation_id", conversationId, "action", "updated"));
        return ApiResult.ok(true);
    }

    @PutMapping("/{conversationId}/summon")
    public ApiResult<Map<String, Object>> setSummon(@PathVariable String conversationId,
                                                    @RequestBody Map<String, Object> body) {
        conversationStore.get(conversationId);
        Map<String, Object> summon = new LinkedHashMap<>();
        summon.put("companion_id", body.get("companion_id"));
        summon.put("memory_ids", body.getOrDefault("memory_ids", new ArrayList<>()));
        summon.put("skill_exclusions", body.getOrDefault("skill_exclusions", new ArrayList<>()));
        summon.put("summoned_at", System.currentTimeMillis());
        conversationStore.patchExtra(conversationId, "summon", summon);
        pushService.push("conversation.listChanged", Map.of("conversation_id", conversationId, "action", "updated"));
        return ApiResult.ok(summon);
    }

    @DeleteMapping("/{conversationId}/summon")
    /**
     * clearsummon。
     * @param conversationId conversationid
     * @return clearSummon的结果
     */
    public ApiResult<Void> clearSummon(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        conversationStore.patchExtra(conversationId, "summon", null);
        return ApiResult.ok();
    }

    @GetMapping("/active-count")
    /**
     * 活跃数量。
     * @return 活跃数量的结果
     */
    public ApiResult<Map<String, Object>> activeCount() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysAiConversation> paging =
                conversationStore.page(1, 500);
        long count = paging.getRecords().stream()
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
    /**
     * 获取associated。
     * @param conversationId conversationid
     * @return 获取associated的结果
     */
    public ApiResult<List<Object>> getAssociated(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前对话服务未实现关联会话查询");
    }

    @GetMapping("/{conversationId}/slash-commands")
    /**
     * 获取slash命令。
     * @param conversationId conversationid
     * @return 获取slash命令的结果
     */
    public ApiResult<List<Map<String, Object>>> getSlashCommands(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前对话服务未实现斜杠命令目录");
    }

    @PostMapping("/{conversationId}/side-question")
    public ApiResult<Map<String, Object>> sideQuestion(@PathVariable String conversationId,
                                                       @RequestBody Map<String, Object> body) {
        conversationStore.get(conversationId);
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前对话服务未实现侧问链路");
    }

    @GetMapping("/{conversationId}/artifacts")
    /**
     * 列表artifacts。
     * @param conversationId conversationid
     * @return 列表artifacts的结果
     */
    public ApiResult<List<Map<String, Object>>> listArtifacts(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前对话服务未实现会话产物存储");
    }

    @PatchMapping("/{conversationId}/artifacts/{conversationArtifactId}")
    public ApiResult<Map<String, Object>> patchArtifact(@PathVariable String conversationId,
                                                        @PathVariable String conversationArtifactId,
                                                        @RequestBody Map<String, Object> body) {
        conversationStore.get(conversationId);
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前对话服务未实现会话产物存储");
    }

    @PutMapping("/{conversationId}/artifacts/{conversationArtifactId}")
    public ApiResult<Map<String, Object>> putArtifact(@PathVariable String conversationId,
                                                      @PathVariable String conversationArtifactId,
                                                      @RequestBody Map<String, Object> body) {
        conversationStore.get(conversationId);
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前对话服务未实现会话产物存储");
    }

    @GetMapping("/{conversationId}/workspace")
    public ApiResult<List<Map<String, Object>>> getWorkspace(@PathVariable String conversationId,
                                                             @RequestParam(required = false) String path,
                                                             @RequestParam(required = false) String search) {
        conversationStore.get(conversationId);
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前对话服务未实现会话工作区接口");
    }

    @GetMapping("/{conversationId}/mode")
    /**
     * 获取mode。
     * @param conversationId conversationid
     * @return 获取mode的结果
     */
    public ApiResult<Map<String, Object>> getMode(@PathVariable String conversationId) {
        SysAiConversation c = conversationStore.get(conversationId);
        Map<String, Object> extra = c.getExtra();
        Object mode = extra == null ? null : extra.get("mode");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode == null ? "chat" : mode);
        return ApiResult.ok(result);
    }

    @PutMapping("/{conversationId}/mode")
    public ApiResult<Map<String, Object>> setMode(@PathVariable String conversationId,
                                                  @RequestBody Map<String, Object> body) {
        String mode = body.get("mode") == null ? "" : String.valueOf(body.get("mode")).trim();
        if (mode.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "mode 不能为空");
        }
        SysAiConversation patch = new SysAiConversation();
        patch.setExtra(Map.of("mode", mode));
        conversationStore.patch(conversationId, patch);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        return ApiResult.ok(result);
    }

    @GetMapping("/{conversationId}/approvals/check")
    public ApiResult<Map<String, Object>> checkApproval(@PathVariable String conversationId,
                                                        @RequestParam String action,
                                                        @RequestParam(required = false) String command_type) {
        conversationStore.get(conversationId);
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前对话服务未实现审批策略");
    }

    @GetMapping("/{conversationId}/confirmations")
    /**
     * 列表confirmations。
     * @param conversationId conversationid
     * @return 列表confirmations的结果
     */
    public ApiResult<List<Object>> listConfirmations(@PathVariable String conversationId) {
        conversationStore.get(conversationId);
        return ApiResult.ok(new ArrayList<>());
    }

    @PostMapping("/{conversationId}/confirmations/{callId}/confirm")
    public ApiResult<Void> confirm(@PathVariable String conversationId,
                                   @PathVariable String callId,
                                   @RequestBody Map<String, Object> body) {
        conversationStore.get(conversationId);
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前对话服务未实现确认请求处理");
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
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param "消息内容不能为空" "消息内容不能为空"
             * @return api异常的结果
             */
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
        throw new ApiException(ApiErrorCode.NOT_IMPLEMENTED, "当前对话服务未实现知识回写重试");
    }

    private void pushTurnError(String conversationId, String turnId, String assistantMsgId,
                               String modelName, Throwable error) {
        String errorMessage = error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? "未知错误" : error.getMessage();
        String errText = "对话失败: " + errorMessage;
        conversationStore.patchRuntime(conversationId, "active_turn_id", null);
        SysAiConversationMessage errMsg = newSysAiConversationMessage(conversationId, "text",
                errText, "assistant", "left");
        errMsg.setMessageId(assistantMsgId);
        errMsg.setMsgId(assistantMsgId);
        conversationStore.addMessage(conversationId, errMsg);

        Map<String, Object> errRuntime = new LinkedHashMap<>();
        errRuntime.put("state", "error");
        errRuntime.put("can_send_message", true);
        errRuntime.put("has_runtime", false);
        errRuntime.put("is_processing", false);
        errRuntime.put("pending_confirmations", 0);
        Map<String, Object> completed = new LinkedHashMap<>();
        completed.put("conversation_id", conversationId);
        completed.put("turn_id", turnId);
        completed.put("status", "error");
        completed.put("state", "error");
        completed.put("error", errText);
        completed.put("can_send_message", true);
        completed.put("runtime", errRuntime);
        if (modelName != null && !modelName.isBlank()) {
            completed.put("model", Map.of("name", modelName, "use_model", modelName));
        }
        completed.put("last_message", Map.of(
                "message_id", assistantMsgId,
                "type", "text",
                "content", errText,
                "status", "error"
        ));
        pushService.push("turn.completed", completed);
        pushService.push("conversation.listChanged",
                Map.of("conversation_id", conversationId, "action", "updated"));
    }

    /**
     * 执行流turn。
     * @param conversationId conversationid
     * @param input 输入
     * @return 执行流turn的结果
     */
    private ApiResult<Map<String, Object>> doStreamTurn(String conversationId, String input) {
        if (input == null || input.isBlank()) {
            /**
             * api异常。
             * @param ApiErrorCode.INVALID_PARAM api错误编码.invalid参数
             * @param "消息内容不能为空" "消息内容不能为空"
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "消息内容不能为空");
        }

        // 1. 保存用户消息
        SysAiConversationMessage userMsg = newSysAiConversationMessage(conversationId, "text", input, "user", "right");
        conversationStore.addMessage(conversationId, userMsg);

 // 生成 turn 与 assistant 消息 标识
        String turnId = conversationStore.newId();
        String assistantMsgId = conversationStore.newId();
        conversationStore.patchRuntime(conversationId, "active_turn_id", turnId);

 // 2. 推送 用户创建
        pushService.push("message.userCreated", Map.of(
                "conversation_id", conversationId,
                "msg_id", userMsg.getMessageId(),
                "content", input,
                "position", "right",
                "status", "finish",
                "created_at", userMsg.getCreatedAt()
        ));

 // 3. 推送 turn.启动
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
            if (fullReply == null || fullReply.isBlank()) {
                pushTurnError(conversationId, turnId, assistantMsgId, modelName,
                        new IllegalStateException("模型返回空回复"));
                return;
            }
            // 5. 保存 assistant 消息
            SysAiConversationMessage assistantMsg = newSysAiConversationMessage(conversationId, "text",
                    fullReply, "assistant", "left");
            assistantMsg.setMessageId(assistantMsgId);
            assistantMsg.setMsgId(assistantMsgId);
            conversationStore.addMessage(conversationId, assistantMsg);
            rememberTurn(conversation, input, fullReply, modelName);
            conversationStore.patchRuntime(conversationId, "active_turn_id", null);

            // 6. 推送 turn.completed
            java.util.Map<String, Object> runtimeMap = new LinkedHashMap<>();
            runtimeMap.put("state", "ai_waiting_input");
            runtimeMap.put("can_send_message", true);
            runtimeMap.put("has_runtime", false);
            runtimeMap.put("is_processing", false);
            runtimeMap.put("pending_confirmations", 0);
            Map<String, Object> completed = new LinkedHashMap<>();
            completed.put("conversation_id", conversationId);
            completed.put("turn_id", turnId);
            completed.put("status", "finished");
            completed.put("state", "ai_waiting_input");
            completed.put("can_send_message", true);
            completed.put("runtime", runtimeMap);
            if (modelName != null && !modelName.isBlank()) {
                completed.put("model", Map.of("name", modelName, "use_model", modelName));
            }
            completed.put("last_message", Map.of(
                    "message_id", assistantMsgId,
                    "type", "text",
                    "content", fullReply,
                    "status", "finish"
            ));
            pushService.push("turn.completed", completed);
            pushService.push("conversation.listChanged",
                    Map.of("conversation_id", conversationId, "action", "updated"));
        }, (error) -> {
            // 失败分支：保存错误 assistant 消息并推送带错误语义的 turn.completed，避免前端卡死
            String errText = error != null && error.getMessage() != null
                    ? "对话失败: " + error.getMessage() : "对话失败";
            SysAiConversationMessage errMsg = newSysAiConversationMessage(conversationId, "text",
                    errText, "assistant", "left");
            errMsg.setMessageId(assistantMsgId);
            errMsg.setMsgId(assistantMsgId);
            conversationStore.addMessage(conversationId, errMsg);

            java.util.Map<String, Object> errRuntime = new LinkedHashMap<>();
            errRuntime.put("state", "error");
            errRuntime.put("can_send_message", true);
            errRuntime.put("has_runtime", false);
            errRuntime.put("is_processing", false);
            errRuntime.put("pending_confirmations", 0);
            pushService.push("turn.completed", Map.of(
                    "conversation_id", conversationId,
                    "turn_id", turnId,
                    "status", "error",
                    "state", "error",
                    "error", errText,
                    "can_send_message", true,
                    "runtime", errRuntime,
                    "model", Map.of("platform", "", "name", modelName != null ? modelName : "",
                            "use_model", modelName != null ? modelName : ""),
                    "last_message", Map.of(
                            "message_id", assistantMsgId,
                            "type", "text",
                            "content", errText,
                            "status", "error"
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

    /**
     * clear消息内部。
     * @param conversationId conversationid
     */
    private void clearMessagesInternal(String conversationId) {
        conversationStore.clearMessages(conversationId);
    }

    /**
     * 新sysaiconversation消息。
     * @param conversationId conversationid
     * @param type 类型
     * @param content 内容
     * @param role 角色
     * @param position 立场
     * @return 新sysAIconversation消息的结果
     */
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

    /**
     * 构建请求。
     * @param conversation conversation
     * @param userInput 用户输入
     * @return 构建请求的结果
     */
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
        injectMemory(msgList, conversation, userInput);
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

    /**
     * 记忆召回并注入：把本轮命中的长期记忆作为一段 system 消息下发。
     *
     * <p>召回失败只降级为"本轮不带记忆"，不阻断对话。</p>
     *
     * @param msgList      待拼装的消息列表
     * @param conversation 当前会话
     * @param userInput    本轮用户输入（相关性打分依据）
     */
    private void injectMemory(List<Map<String, Object>> msgList, SysAiConversation conversation, String userInput) {
        String owner = memoryOwner(conversation);
        if (owner == null) {
            return;
        }
        try {
            AiMemoryRecallDTO recall = memoryService.recall(owner, conversation.getConversationId(), userInput, true);
            if (recall == null || recall.promptBlock() == null || recall.promptBlock().isBlank()) {
                return;
            }
            Map<String, Object> memory = new LinkedHashMap<>();
            memory.put("role", "system");
            memory.put("content", recall.promptBlock());
            msgList.add(memory);
            log.info("[Chat] 记忆注入 conversation={} user={} 条数={}",
                    conversation.getConversationId(), owner, recall.items().size());
        } catch (Exception e) {
            log.warn("[Chat] 记忆召回失败（本轮不注入）: {}", e.getMessage());
        }
    }

    /**
     * 记忆归属人：随会话归属，历史会话无归属时回落到当前请求身份。
     *
     * @param conversation 当前会话
     * @return 归属用户，无法确定时返回 {@code null}
     */
    private String memoryOwner(SysAiConversation conversation) {
        String owner = conversation.getUserId();
        if (owner != null && !owner.isBlank()) {
            return owner;
        }
        String current = AiUserContext.currentUserId();
        return current == null || current.isBlank() ? null : current;
    }

    /**
     * 轮次结束后的记忆抽取：异步执行，避免模型抽取拖慢 turn.completed 推送。
     *
     * @param conversation 当前会话
     * @param input        本轮用户输入
     * @param fullReply    本轮模型回复
     * @param model        本轮模型名称
     */
    private void rememberTurn(SysAiConversation conversation, String input, String fullReply, String model) {
        String owner = memoryOwner(conversation);
        if (owner == null || fullReply == null || fullReply.isBlank()) {
            return;
        }
        String conversationId = conversation.getConversationId();
        CompletableFuture.runAsync(AiUserContext.capturing(() -> {
            try {
                memoryService.extractAndRecord(owner, conversationId, input, fullReply, model);
            } catch (Exception e) {
                log.warn("[Chat] 轮次记忆抽取失败 conversation={}: {}", conversationId, e.getMessage());
            }
        }));
    }

    /**
     * 转为响应。
     * @param c c
     * @return 转为响应的结果
     */
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

    /**
     * 转为消息响应。
     * @param m m
     * @return 转为消息响应的结果
     */
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
    /**
     * uncheckedcast。
     * @param obj obj
     * @return uncheckedCast的结果
     */
    private static Map<String, Object> uncheckedCast(Object obj) {
        return (Map<String, Object>) obj;
    }
}

/**
 * 消息全局检索控制器（对接 /api/消息 契约，独立于会话命名空间）。
 *
 * <p>前端 {@code ipcBridge} 的 {@code /api/messages/search} 不在 {@code /api/conversations}
 * 之下，故以独立 {@code @RestController} 承载，复用同一套 {@link ApiResult} 信封。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
class ApiMessageSearchController {

    private final SysAiConversationStore conversationStore;

    /**
     * 全局消息检索。
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
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysAiConversationMessage> paging =
                conversationStore.searchMessages(keyword, page, page_size);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", paging.getRecords().stream().map(this::toResponse).toList());
        result.put("total", paging.getTotal());
        result.put("has_more", paging.getCurrent() * paging.getSize() < paging.getTotal());
        return ApiResult.ok(result);
    }

    private Map<String, Object> toResponse(SysAiConversationMessage message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message_id", message.getMessageId());
        result.put("conversation_id", message.getConversationId());
        result.put("msg_id", message.getMsgId());
        result.put("type", message.getType());
        result.put("content", message.getContent());
        result.put("position", message.getPosition());
        result.put("status", message.getStatus());
        result.put("hidden", message.isHidden());
        result.put("created_at", message.getCreatedAt());
        return result;
    }
}

