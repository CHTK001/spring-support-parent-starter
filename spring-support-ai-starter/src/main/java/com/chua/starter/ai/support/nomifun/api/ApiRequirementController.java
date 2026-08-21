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
import java.util.concurrent.atomic.AtomicLong;

/**
 * 需求（Requirement）控制器，替代 nomicore 的 {@code /api/requirements} 契约。
 *
 * <p>维护需求条目（{@code requirement_id} 主键）、标签统计、看板、自动作业（AutoWork）与
 * 标签绑定。关键变更推送 {@code requirement.created / requirement.updated /
 * requirement.statusChanged / requirement.deleted} 与 {@code autowork.*} 事件。
 * 当前为进程内内存实现。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/requirements")
@RequiredArgsConstructor
@Slf4j
public class ApiRequirementController {

    /**
     * 主键生成与基础存储工具
     */
    private final ConversationStore conversationStore;

    /**
     * WS 事件推送服务
     */
    private final ApiWebSocketPushService pushService;

    /**
     * 需求条目：requirement_id → 需求
     */
    private final Map<String, Map<String, Object>> requirements = new ConcurrentHashMap<>();

    /**
     * 自增展示序号
     */
    private final AtomicLong displaySeq = new AtomicLong(1);

    @GetMapping
    public ApiResult<Map<String, Object>> list(@RequestParam(required = false) String tag,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String conversation_id,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required = false) String order_by,
                                              @RequestParam(required = false) String order,
                                              @RequestParam(required = false, defaultValue = "1") int page,
                                              @RequestParam(required = false, defaultValue = "50") int page_size) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> req : requirements.values()) {
            if (tag != null && !tag.equals(req.get("tag"))) {
                continue;
            }
            if (status != null && !status.equals(req.get("status"))) {
                continue;
            }
            if (conversation_id != null && !conversation_id.equals(req.get("owner_conversation_id"))) {
                continue;
            }
            if (q != null && !((String) req.get("title")).toLowerCase().contains(q.toLowerCase())) {
                continue;
            }
            items.add(req);
        }
        Map<String, Object> pageResult = new LinkedHashMap<>();
        pageResult.put("items", items);
        pageResult.put("total", items.size());
        pageResult.put("has_more", false);
        return ApiResult.ok(pageResult);
    }

    @GetMapping("/tags")
    public ApiResult<List<Map<String, Object>>> tags() {
        Map<String, Map<String, Object>> summary = new LinkedHashMap<>();
        for (Map<String, Object> req : requirements.values()) {
            String tag = (String) req.get("tag");
            Map<String, Object> s = summary.computeIfAbsent(tag, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tag", k);
                for (String key : new String[]{"pending", "in_progress", "done", "failed", "cancelled", "needs_review"}) {
                    m.put(key, 0);
                }
                m.put("total", 0);
                m.put("paused", false);
                return m;
            });
            String status = (String) req.get("status");
            if (s.containsKey(status)) {
                s.put(status, ((Number) s.get(status)).intValue() + 1);
            }
            s.put("total", ((Number) s.get("total")).intValue() + 1);
        }
        return ApiResult.ok(new ArrayList<>(summary.values()));
    }

    @GetMapping("/board")
    public ApiResult<Map<String, Object>> board(@RequestParam String tag) {
        Map<String, Object> board = new LinkedHashMap<>();
        board.put("tag", tag);
        for (String key : new String[]{"pending", "in_progress", "done", "failed", "cancelled", "needs_review"}) {
            board.put(key, new ArrayList<>());
        }
        return ApiResult.ok(board);
    }

    @GetMapping("/tag-bindings")
    public ApiResult<List<Map<String, Object>>> tagBindings() {
        return ApiResult.ok(new ArrayList<>());
    }

    @PostMapping("/autowork")
    public ApiResult<Map<String, Object>> setAutoWork(@RequestBody Map<String, Object> body) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("kind", body.getOrDefault("kind", "conversation"));
        state.put("target_id", body.get("target_id"));
        state.put("run_state", body.getOrDefault("run_state", "off"));
        state.put("current_requirement_id", body.get("current_requirement_id"));
        pushService.push("autowork.statusChanged", state);
        return ApiResult.ok(state);
    }

    @GetMapping("/autowork/{kind}/{targetId}")
    public ApiResult<Map<String, Object>> getAutoWork(@PathVariable String kind,
                                                      @PathVariable String targetId) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("kind", kind);
        state.put("target_id", targetId);
        state.put("run_state", "off");
        state.put("current_requirement_id", null);
        return ApiResult.ok(state);
    }

    @PostMapping("/tags/{tag}/resume")
    public ApiResult<Map<String, Object>> resumeTag(@PathVariable String tag,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tag", tag);
        summary.put("total", 0);
        summary.put("pending", 0);
        summary.put("in_progress", 0);
        summary.put("done", 0);
        summary.put("failed", 0);
        summary.put("cancelled", 0);
        summary.put("needs_review", 0);
        summary.put("paused", false);
        pushService.push("autowork.tagPaused", Map.of("tag", tag, "reason", "manual_resume"));
        return ApiResult.ok(summary);
    }

    @GetMapping("/{requirementId}")
    public ApiResult<Map<String, Object>> get(@PathVariable String requirementId) {
        return ApiResult.ok(requireRequirement(requirementId));
    }

    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String requirementId = conversationStore.newId();
        long now = System.currentTimeMillis();
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("requirement_id", requirementId);
        req.put("display_no", displaySeq.getAndIncrement());
        req.put("title", body.getOrDefault("title", "未命名需求"));
        req.put("content", body.getOrDefault("content", ""));
        req.put("tag", body.getOrDefault("tag", "default"));
        req.put("order_key", body.getOrDefault("order_key", String.valueOf(now)));
        req.put("status", body.getOrDefault("status", "pending"));
        req.put("completion_note", null);
        req.put("owner_conversation_id", body.get("owner_conversation_id"));
        req.put("owner_terminal_id", body.get("owner_terminal_id"));
        req.put("started_at", null);
        req.put("completed_at", null);
        req.put("attempt_count", 0);
        req.put("created_by", body.getOrDefault("created_by", "user"));
        req.put("created_at", now);
        req.put("updated_at", now);
        requirements.put(requirementId, req);
        pushService.push("requirement.created", req);
        return ApiResult.ok(req);
    }

    @PutMapping("/{requirementId}")
    public ApiResult<Map<String, Object>> update(@PathVariable String requirementId,
                                                 @RequestBody Map<String, Object> body) {
        Map<String, Object> req = requireRequirement(requirementId);
        String oldStatus = (String) req.get("status");
        if (body.containsKey("title")) {
            req.put("title", body.get("title"));
        }
        if (body.containsKey("content")) {
            req.put("content", body.get("content"));
        }
        if (body.containsKey("tag")) {
            req.put("tag", body.get("tag"));
        }
        if (body.containsKey("order_key")) {
            req.put("order_key", body.get("order_key"));
        }
        if (body.containsKey("status")) {
            req.put("status", body.get("status"));
        }
        if (body.containsKey("completion_note")) {
            req.put("completion_note", body.get("completion_note"));
        }
        req.put("updated_at", System.currentTimeMillis());
        pushService.push("requirement.updated", req);
        if (!oldStatus.equals(req.get("status"))) {
            pushService.push("requirement.statusChanged", req);
        }
        return ApiResult.ok(req);
    }

    @DeleteMapping("/{requirementId}")
    public ApiResult<Void> remove(@PathVariable String requirementId) {
        requireRequirement(requirementId);
        requirements.remove(requirementId);
        pushService.push("requirement.deleted", Map.of("requirement_id", requirementId));
        return ApiResult.ok();
    }

    @PostMapping("/batch-delete")
    public ApiResult<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> body) {
        int deleted = 0;
        Object raw = body.get("requirement_ids");
        if (raw instanceof List) {
            for (Object id : (List<?>) raw) {
                if (requirements.remove(String.valueOf(id)) != null) {
                    deleted++;
                    pushService.push("requirement.deleted", Map.of("requirement_id", String.valueOf(id)));
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        return ApiResult.ok(result);
    }

    private Map<String, Object> requireRequirement(String requirementId) {
        Map<String, Object> req = requirements.get(requirementId);
        if (req == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "需求不存在: " + requirementId);
        }
        return req;
    }
}
