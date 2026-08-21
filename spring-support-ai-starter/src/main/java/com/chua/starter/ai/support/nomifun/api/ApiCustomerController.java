package com.chua.starter.ai.support.nomifun.api;

import com.chua.starter.ai.support.nomifun.common.ApiErrorCode;
import com.chua.starter.ai.support.nomifun.common.ApiException;
import com.chua.starter.ai.support.nomifun.common.ApiResult;
import com.chua.starter.ai.support.nomifun.store.ConversationStore;
import com.chua.starter.ai.support.nomifun.ApiWebSocketPushService;
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
 * 客服（Customer Service）控制器，替代 nomicore 的 {@code /api/customer-service/*} 契约。
 *
 * <p>维护客服智能体（{@code cs_agent_id}）、知识笔记（{@code cs_note_id}）、对话通道
 * （{@code cs_dialogue_id}）与消息（{@code cs_message_id}）。当前为进程内内存实现。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/customer-service")
@RequiredArgsConstructor
@Slf4j
public class ApiCustomerController {

    /**
     * 主键生成与基础存储工具
     */
    private final ConversationStore conversationStore;

    /**
     * WS 事件推送服务
     */
    private final ApiWebSocketPushService pushService;

    /**
     * 客服智能体：cs_agent_id → 智能体
     */
    private final Map<String, Map<String, Object>> agents = new ConcurrentHashMap<>();

    /**
     * 知识笔记：cs_note_id → 笔记
     */
    private final Map<String, Map<String, Object>> notes = new ConcurrentHashMap<>();

    /**
     * 对话通道：cs_dialogue_id → 对话
     */
    private final Map<String, Map<String, Object>> dialogues = new ConcurrentHashMap<>();

    /**
     * 对话消息：cs_dialogue_id → 消息列表
     */
    private final Map<String, List<Map<String, Object>>> messages = new ConcurrentHashMap<>();

    // ---- agents ----

    @GetMapping("/agents")
    public ApiResult<List<Map<String, Object>>> listAgents() {
        return ApiResult.ok(new ArrayList<>(agents.values()));
    }

    @PostMapping("/agents")
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
    public ApiResult<Void> removeAgent(@PathVariable String csAgentId) {
        requireAgent(csAgentId);
        agents.remove(csAgentId);
        return ApiResult.ok();
    }

    @GetMapping("/agents/{csAgentId}/bindings")
    public ApiResult<List<Map<String, Object>>> listBindings(@PathVariable String csAgentId) {
        requireAgent(csAgentId);
        return ApiResult.ok(new ArrayList<>());
    }

    @PutMapping("/agents/{csAgentId}/bindings")
    public ApiResult<List<Map<String, Object>>> replaceBindings(@PathVariable String csAgentId,
                                                                @RequestBody Map<String, Object> body) {
        requireAgent(csAgentId);
        return ApiResult.ok(new ArrayList<>());
    }

    // ---- notes ----

    @GetMapping("/notes")
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
    public ApiResult<Void> removeNote(@PathVariable String csNoteId) {
        requireNote(csNoteId);
        notes.remove(csNoteId);
        return ApiResult.ok();
    }

    // ---- dialogues ----

    @GetMapping("/dialogues")
    public ApiResult<List<Map<String, Object>>> listDialogues(@RequestParam String cs_agent_id) {
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
    public ApiResult<List<Map<String, Object>>> listDialogueMessages(@PathVariable String csDialogueId) {
        requireDialogue(csDialogueId);
        return ApiResult.ok(messages.getOrDefault(csDialogueId, new ArrayList<>()));
    }

    private Map<String, Object> requireAgent(String csAgentId) {
        Map<String, Object> agent = agents.get(csAgentId);
        if (agent == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "客服智能体不存在: " + csAgentId);
        }
        return agent;
    }

    private Map<String, Object> requireNote(String csNoteId) {
        Map<String, Object> note = notes.get(csNoteId);
        if (note == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "笔记不存在: " + csNoteId);
        }
        return note;
    }

    private Map<String, Object> requireDialogue(String csDialogueId) {
        Map<String, Object> dialogue = dialogues.get(csDialogueId);
        if (dialogue == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "对话不存在: " + csDialogueId);
        }
        return dialogue;
    }
}
