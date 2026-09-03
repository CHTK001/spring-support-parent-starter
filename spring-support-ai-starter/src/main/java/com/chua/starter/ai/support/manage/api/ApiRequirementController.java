package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.manage.store.SysAiRequirementStore;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
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
import java.util.concurrent.atomic.AtomicLong;

/**
 * 需求（Requirement）控制器，替代 nomicore 的 {@code /api/requirements} 契约。
 *
 * <p>维护需求条目（{@code requirement_id} 主键）、标签统计、看板、自动作业（AutoWork）与
 * 标签绑定。关键变更推送 {@code requirement.created / requirement.updated /
 * requirement.statusChanged / requirement.deleted} 与 {@code autowork.*} 事件。
 * 数据持久化到 {@code sys_ai_requirement} 表。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/requirements")
@RequiredArgsConstructor
@Slf4j
public class ApiRequirementController {

    private final SysAiConversationStore conversationStore;
    private final ApiWebSocketPushService pushService;
    private final SysAiRequirementStore requirementStore;
    private final AtomicLong displaySeq = new AtomicLong(1);

    // ======================== 需求 CRUD ========================

    @GetMapping
    public ApiResult<Map<String, Object>> list(@RequestParam(required = false) String tag,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String conversation_id,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) String order_by,
                                               @RequestParam(required = false) String order,
                                               @RequestParam(required = false, defaultValue = "1") int page,
                                               @RequestParam(required = false, defaultValue = "50") int page_size) {
        Map<String, Object> result = requirementStore.list(tag, status, q, order_by, order, page, page_size);
        if (conversation_id != null && !conversation_id.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
            items.removeIf(r -> !conversation_id.equals(r.get("owner_conversation_id")));
            result.put("total", (long) items.size());
            result.put("has_more", false);
        }
        return ApiResult.ok(result);
    }

    @GetMapping("/tags")
    public ApiResult<List<Map<String, Object>>> tags() {
        return ApiResult.ok(requirementStore.tags());
    }

    @GetMapping("/board")
    public ApiResult<Map<String, Object>> board(@RequestParam String tag) {
        return ApiResult.ok(requirementStore.board(tag));
    }

    @GetMapping("/{requirementId}")
    public ApiResult<Map<String, Object>> get(@PathVariable String requirementId) {
        Map<String, Object> req = requirementStore.get(requirementId);
        if (req == null) throw new ApiException(ApiErrorCode.NOT_FOUND, "需求不存在: " + requirementId);
        return ApiResult.ok(req);
    }

    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> req = requirementStore.create(body);
        pushService.push("requirement.created", req);
        return ApiResult.ok(req);
    }

    @PutMapping("/{requirementId}")
    public ApiResult<Map<String, Object>> update(@PathVariable String requirementId,
                                                 @RequestBody Map<String, Object> body) {
        Map<String, Object> req = requirementStore.update(requirementId, body);
        if (req == null) throw new ApiException(ApiErrorCode.NOT_FOUND, "需求不存在: " + requirementId);
        boolean changed = Boolean.TRUE.equals(req.get("__status_changed__"));
        pushService.push("requirement.updated", req);
        if (changed) pushService.push("requirement.statusChanged", req);
        return ApiResult.ok(req);
    }

    @DeleteMapping("/{requirementId}")
    public ApiResult<Void> remove(@PathVariable String requirementId) {
        if (!requirementStore.remove(requirementId)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "需求不存在: " + requirementId);
        }
        pushService.push("requirement.deleted", Map.of("requirement_id", requirementId));
        return ApiResult.ok();
    }

    @PostMapping("/batch-delete")
    public ApiResult<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("requirement_ids");
        int deleted = requirementStore.batchDelete(ids);
        for (String id : ids) {
            pushService.push("requirement.deleted", Map.of("requirement_id", id));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        return ApiResult.ok(result);
    }

    // ======================== AutoWork ========================

    @PostMapping("/autowork")
    public ApiResult<Map<String, Object>> setAutoWork(@RequestBody Map<String, Object> body) {
        String kind = body.getOrDefault("kind", "conversation").toString();
        String targetId = body.get("target_id") != null ? body.get("target_id").toString() : null;
        boolean enabled = Boolean.parseBoolean(body.getOrDefault("enabled", false).toString());
        String tag = body.get("tag") != null ? body.get("tag").toString() : null;
        boolean fromAdmin = Boolean.parseBoolean(body.getOrDefault("from_admin", false).toString());
        Map<String, Object> state = requirementStore.setAutoWork(kind, targetId, enabled, tag, fromAdmin);
        pushService.push("autowork.statusChanged", state);
        return ApiResult.ok(state);
    }

    @GetMapping("/autowork/{kind}/{targetId}")
    public ApiResult<Map<String, Object>> getAutoWork(@PathVariable String kind, @PathVariable String targetId) {
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
        boolean requeueFailed = body != null && Boolean.TRUE.equals(body.get("requeue_failed"));
        Map<String, Object> summary = requirementStore.resumeTag(tag, requeueFailed);
        pushService.push("autowork.tagPaused", Map.of("tag", tag, "reason", "manual_resume"));
        return ApiResult.ok(summary);
    }

    // ======================== 数据源管理 ========================

    /**
     * 获取所有数据源列表。
     */
    @GetMapping("/data-sources")
    public ApiResult<List<Map<String, Object>>> listDataSources() {
        return ApiResult.ok(requirementStore.listDataSources());
    }

    /**
     * 获取可用的数据源类型列表。
     */
    @GetMapping("/data-sources/types")
    public ApiResult<List<String>> dataSourceTypes() {
        return ApiResult.ok(requirementStore.availableTypes());
    }

    /**
     * 创建数据源。
     */
    @PostMapping("/data-sources")
    public ApiResult<Map<String, Object>> createDataSource(@RequestBody Map<String, Object> body) {
        Map<String, Object> ds = requirementStore.createDataSource(body);
        if (ds == null) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "不支持的数据源类型");
        }
        return ApiResult.ok(ds);
    }

    /**
     * 更新数据源。
     */
    @PutMapping("/data-sources/{id}")
    public ApiResult<Map<String, Object>> updateDataSource(@PathVariable String id,
                                                            @RequestBody Map<String, Object> body) {
        Map<String, Object> ds = requirementStore.updateDataSource(id, body);
        if (ds == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "数据源不存在: " + id);
        }
        return ApiResult.ok(ds);
    }

    /**
     * 删除数据源。
     */
    @DeleteMapping("/data-sources/{id}")
    public ApiResult<Void> removeDataSource(@PathVariable String id) {
        if (!requirementStore.removeDataSource(id)) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "数据源不存在: " + id);
        }
        return ApiResult.ok();
    }

    /**
     * 测试数据源连接。
     */
    @PostMapping("/data-sources/{id}/test")
    public ApiResult<Map<String, Object>> testDataSource(@PathVariable String id) {
        boolean ok = requirementStore.testDataSource(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("connected", ok);
        return ApiResult.ok(result);
    }

    /**
     * 触发数据源同步。
     */
    @PostMapping("/data-sources/{id}/sync")
    public ApiResult<Map<String, Object>> syncDataSource(@PathVariable String id) {
        Map<String, Object> result = requirementStore.syncDataSource(id);
        Object error = result.get("error");
        if (error != null) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, error.toString());
        }
        return ApiResult.ok(result);
    }
}
