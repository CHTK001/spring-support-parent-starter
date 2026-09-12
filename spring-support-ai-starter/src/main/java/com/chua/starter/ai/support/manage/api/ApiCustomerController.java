package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
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

/**
* 客服（习俗 服务）控制器，替代 nomicore 的 {@code /api/customer-service/*} 契约。
*
* <p>维护客服智能体（{@code cs_agent_id}）、知识笔记（{@code cs_note_id}）、对话通道
* （{@code cs_dialogue_id}）与消息（{@code cs_message_id}）。当前为进程内内存实现。</p>
*
* @since 4.0.0.42
* @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/customer-service")
@RequiredArgsConstructor
@Slf4j
public class ApiCustomerController {

    /**
    * 主键生成与基础存储工具
     */
    private final SysAiConversationStore conversationStore;

    /**
    * WS 事件推送服务
     */
    private final ApiWebSocketPushService pushService;

    /**
    * 客服智能体：C_智能体_标识 → 智能体
     */
    private final Map<String, Map<String, Object>> agents = new ConcurrentHashMap<>();

    /**
    * 知识笔记：C_笔记_标识 → 笔记
     */
    private final Map<String, Map<String, Object>> notes = new ConcurrentHashMap<>();

    /**
    * 对话通道：C_dialogue_标识 → 对话
     */
    private final Map<String, Map<String, Object>> dialogues = new ConcurrentHashMap<>();

    /**
    * 对话消息：C_dialogue_标识 → 消息列表
     */
    private final Map<String, List<Map<String, Object>>> messages = new ConcurrentHashMap<>();

 // ---- 智能体 ----

    @GetMapping("/agents")
    /**
    * 列表智能体。
    * @return 列表智能体的结果
     */
    public ApiResult<List<Map<String, Object>>> listAgents() {
        return ApiResult.ok(new ArrayList<>(agents.values()));
    }

    @PostMapping("/agents")
    /**
    * 创建智能体。
    * @param body 主体
    * @return 创建智能体的结果
     */
    public ApiResult<Map<String, Object>> createAgent(@RequestBody Map<String, Object> body) {
        String agentId = conversationStore.newId();
        Map<String, Object> agent = new LinkedHashMap<>();
        agent.put("cs_agent_id", agentId);
        agent.put("name", body.getOrDefault("name", "未命名客服"));
        agent.put("greeting", body.getOrDefault("greeting", ""));
        agent.put("persona", body.getOrDefault("persona", ""));
        agent.put("service_policy", body.getOrDefault("service_policy", ""));
        agent.put("provider_id", body.get("provider_id"));
        agent.put("model", body.get("model"));
        agent.put("knowledge_base_ids", body.getOrDefault("knowledge_base_ids", new ArrayList<>()));
        agent.put("enabled", body.getOrDefault("enabled", true));
        agent.put("max_concurrent", body.getOrDefault("max_concurrent", 4));
        agent.put("audit_retention_days", body.getOrDefault("audit_retention_days", 30));
        long now = System.currentTimeMillis();
        agent.put("created_at", now);
        agent.put("updated_at", now);
        agents.put(agentId, agent);
        return ApiResult.ok(agent);
    }

    @GetMapping("/agents/{csAgentId}")
    /**
    * 获取智能体。
    * @param csAgentId C智能体标识
    * @return 获取智能体的结果
     */
    public ApiResult<Map<String, Object>> getAgent(@PathVariable String csAgentId) {
        return ApiResult.ok(requireAgent(csAgentId));
    }

    @PatchMapping("/agents/{csAgentId}")
    public ApiResult<Map<String, Object>> patchAgent(@PathVariable String csAgentId,
                                                      @RequestBody Map<String, Object> body) {
        Map<String, Object> agent = requireAgent(csAgentId);
        body.forEach((k, v) -> {
            if (!"cs_agent_id".equals(k) && !"created_at".equals(k)) {
                agent.put(k, v);
            }
        });
        agent.put("updated_at", System.currentTimeMillis());
        return ApiResult.ok(agent);
    }

    @DeleteMapping("/agents/{csAgentId}")
    /**
    * 移除智能体。
    * @param csAgentId C智能体标识
    * @return 移除智能体的结果
     */
    public ApiResult<Void> removeAgent(@PathVariable String csAgentId) {
        /**
        * require智能体。
        * @param csAgentId C智能体标识
         */
        requireAgent(csAgentId);
        agents.remove(csAgentId);
        return ApiResult.ok();
    }

    @GetMapping("/agents/{csAgentId}/bindings")
    /**
    * 列表bindings。
    * @param csAgentId C智能体标识
    * @return 列表bindings的结果
     */
    public ApiResult<List<Map<String, Object>>> listBindings(@PathVariable String csAgentId) {
        /**
        * require智能体。
        * @param csAgentId C智能体标识
         */
        requireAgent(csAgentId);
        return ApiResult.ok(new ArrayList<>());
    }

    @PutMapping("/agents/{csAgentId}/bindings")
    public ApiResult<List<Map<String, Object>>> replaceBindings(@PathVariable String csAgentId,
                                                                @RequestBody Map<String, Object> body) {
        /**
        * require智能体。
        * @param csAgentId C智能体标识
         */
        requireAgent(csAgentId);
        return ApiResult.ok(new ArrayList<>());
    }

 // ---- 笔记 ----

    @GetMapping("/notes")
    /**
    * 列表笔记。
    * @param false false
    * @return 列表笔记的结果
     */
    public ApiResult<List<Map<String, Object>>> listNotes(@RequestParam(required = false) String cs_agent_id) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> note : notes.values()) {
            if (cs_agent_id == null || cs_agent_id.equals(note.get("cs_agent_id"))) {
                result.add(note);
            }
        }
        return ApiResult.ok(result);
    }

    @PostMapping("/notes")
    /**
    * 创建笔记。
    * @param body 主体
    * @return 创建笔记的结果
     */
    public ApiResult<Map<String, Object>> createNote(@RequestBody Map<String, Object> body) {
        String noteId = conversationStore.newId();
        Map<String, Object> note = new LinkedHashMap<>();
        note.put("cs_note_id", noteId);
        note.put("cs_agent_id", body.get("cs_agent_id"));
        note.put("kind", body.getOrDefault("kind", "faq"));
        note.put("content", body.getOrDefault("content", ""));
        note.put("aliases", body.getOrDefault("aliases", ""));
        note.put("enabled", body.getOrDefault("enabled", true));
        long now = System.currentTimeMillis();
        note.put("created_at", now);
        note.put("updated_at", now);
        notes.put(noteId, note);
        return ApiResult.ok(note);
    }

    @PatchMapping("/notes/{csNoteId}")
    public ApiResult<Map<String, Object>> patchNote(@PathVariable String csNoteId,
                                                    @RequestBody Map<String, Object> body) {
        Map<String, Object> note = requireNote(csNoteId);
        if (body.containsKey("kind")) {
            note.put("kind", body.get("kind"));
        }
        if (body.containsKey("content")) {
            note.put("content", body.get("content"));
        }
        if (body.containsKey("aliases")) {
            note.put("aliases", body.get("aliases"));
        }
        if (body.containsKey("enabled")) {
            note.put("enabled", body.get("enabled"));
        }
        note.put("updated_at", System.currentTimeMillis());
        return ApiResult.ok(note);
    }

    @DeleteMapping("/notes/{csNoteId}")
    /**
    * 移除笔记。
    * @param csNoteId C笔记标识
    * @return 移除笔记的结果
     */
    public ApiResult<Void> removeNote(@PathVariable String csNoteId) {
        /**
        * require笔记。
        * @param csNoteId C笔记标识
         */
        requireNote(csNoteId);
        notes.remove(csNoteId);
        return ApiResult.ok();
    }

    // ---- dialogues ----

    @GetMapping("/dialogues")
    /**
    * 列表dialogues。
    * @param cs_agent_id C智能体标识
    * @return 列表dialogues的结果
     */
    public ApiResult<List<Map<String, Object>>> listDialogues(@RequestParam String cs_agent_id) {
        /**
        * require智能体。
        * @param cs_agent_id C智能体标识
         */
        requireAgent(cs_agent_id);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> dialogue : dialogues.values()) {
            if (cs_agent_id.equals(dialogue.get("cs_agent_id"))) {
                result.add(dialogue);
            }
        }
        return ApiResult.ok(result);
    }

    @GetMapping("/dialogues/{csDialogueId}/messages")
    /**
    * 列表dialogue消息。
    * @param csDialogueId Cdialogueid
    * @return 列表dialogue消息的结果
     */
    public ApiResult<List<Map<String, Object>>> listDialogueMessages(@PathVariable String csDialogueId) {
        /**
        * requiredialogue。
        * @param csDialogueId Cdialogueid
         */
        requireDialogue(csDialogueId);
        return ApiResult.ok(messages.getOrDefault(csDialogueId, new ArrayList<>()));
    }

    /**
    * require智能体。
    * @param csAgentId C智能体标识
    * @return require智能体的结果
     */
    private Map<String, Object> requireAgent(String csAgentId) {
        Map<String, Object> agent = agents.get(csAgentId);
        if (agent == null) {
            /**
            * api异常。
            * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
            * @param csAgentId C智能体标识
            * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "客服智能体不存在: " + csAgentId);
        }
        return agent;
    }

    /**
    * require笔记。
    * @param csNoteId C笔记标识
    * @return require笔记的结果
     */
    private Map<String, Object> requireNote(String csNoteId) {
        Map<String, Object> note = notes.get(csNoteId);
        if (note == null) {
            /**
            * api异常。
            * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
            * @param csNoteId C笔记标识
            * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "笔记不存在: " + csNoteId);
        }
        return note;
    }

    /**
    * requiredialogue。
    * @param csDialogueId Cdialogueid
    * @return requireDialogue的结果
     */
    private Map<String, Object> requireDialogue(String csDialogueId) {
        Map<String, Object> dialogue = dialogues.get(csDialogueId);
        if (dialogue == null) {
            /**
            * api异常。
            * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
            * @param csDialogueId Cdialogueid
            * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "对话不存在: " + csDialogueId);
        }
        return dialogue;
    }
}
