package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
* 智能决策值守（IDMM）控制器，替代 nomicore 的 {@code /api/idmm} 契约。
*
* <p>维护会话/终端两类目标的 IDMM 状态与干预记录。状态变更推送
* {@code idmm.statusChanged} 与 {@code idmm.intervention} 事件。当前为进程内内存实现。</p>
*
* @since 4.0.0.42
* @author CH
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/idmm")
@RequiredArgsConstructor
@Slf4j
public class ApiIdmmController {

    /**
    * 主键生成与基础存储工具
     */
    private final SysAiConversationStore conversationStore;

    /**
    * WS 事件推送服务
     */
    private final ApiWebSocketPushService pushService;

    /**
    * IDMM 状态：种类:Target_标识 → 状态
     */
    private final Map<String, Map<String, Object>> states = new ConcurrentHashMap<>();

    /**
    * IDMM 干预记录：种类:Target_标识 → 记录列表
     */
    private final Map<String, List<Map<String, Object>>> logs = new ConcurrentHashMap<>();

    @PostMapping
    /**
    * 设置。
    * @param body 主体
    * @return 设置的结果
     */
    public ApiResult<Map<String, Object>> set(@RequestBody Map<String, Object> body) {
        String key = keyOf(body.get("kind"), body.get("target_id"));
        Map<String, Object> state = states.computeIfAbsent(key, k -> defaultState(body.get("kind"), body.get("target_id")));
        /**
        * 合并状态。
        * @param state 状态
        * @param body 主体
         */
        mergeState(state, body);
        pushService.push("idmm.statusChanged", state);
        return ApiResult.ok(state);
    }

    @GetMapping("/{kind}/{targetId}")
    public ApiResult<Map<String, Object>> getStatus(@PathVariable String kind,
                                                    @PathVariable String targetId) {
        String key = keyOf(kind, targetId);
        Map<String, Object> state = states.computeIfAbsent(key, k -> defaultState(kind, targetId));
        return ApiResult.ok(state);
    }

    @PostMapping("/{kind}/{targetId}/intervene")
    public ApiResult<Map<String, Object>> intervene(@PathVariable String kind,
                                                    @PathVariable String targetId,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        String key = keyOf(kind, targetId);
        Map<String, Object> state = states.computeIfAbsent(key, k -> defaultState(kind, targetId));
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("intervention_id", conversationStore.newId());
        record.put("target_kind", kind);
        record.put("target_id", targetId);
        record.put("watch", "decision");
        record.put("at", System.currentTimeMillis());
        record.put("stall_class", "manual");
        record.put("tier_used", "rule");
        record.put("category", "manual");
        record.put("action", "intervene");
        record.put("outcome", "ok");
        record.put("reason", body == null ? null : (String) body.get("reason"));
        logs.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        state.put("interventions_count", ((Number) state.get("interventions_count")).intValue() + 1);
        state.put("last_intervention_at", record.get("at"));
        pushService.push("idmm.intervention", record);
        pushService.push("idmm.statusChanged", state);
        return ApiResult.ok(state);
    }

    @GetMapping("/{kind}/{targetId}/log")
    public ApiResult<List<Map<String, Object>>> getLog(@PathVariable String kind,
                                                       @PathVariable String targetId,
                                                       @RequestParam(required = false, defaultValue = "50") int limit) {
        String key = keyOf(kind, targetId);
        List<Map<String, Object>> list = logs.getOrDefault(key, new ArrayList<>());
        return ApiResult.ok(list);
    }

    @DeleteMapping("/{kind}/{targetId}/log")
    public ApiResult<Void> clearLog(@PathVariable String kind,
                                    @PathVariable String targetId) {
        logs.remove(keyOf(kind, targetId));
        return ApiResult.ok();
    }

    /**
    * 钥匙的。
    * @param kind 种类
    * @param targetId Targetid
    * @return 钥匙的的结果
     */
    private String keyOf(Object kind, Object targetId) {
        return kind + ":" + targetId;
    }

    /**
    * 默认状态。
    * @param kind 种类
    * @param targetId Targetid
    * @return 默认状态的结果
     */
    private Map<String, Object> defaultState(Object kind, Object targetId) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("kind", kind);
        state.put("target_id", targetId);
        state.put("enabled", false);
        state.put("run_state", "off");
        state.put("interventions_count", 0);
        state.put("last_signal", null);
        state.put("last_intervention_at", null);
        state.put("sidecar_provider_resolved", false);
        state.put("config", null);
        return state;
    }

    /**
    * 合并状态。
    * @param state 状态
    * @param body 主体
     */
    private void mergeState(Map<String, Object> state, Map<String, Object> body) {
        if (body.containsKey("enabled")) {
            state.put("enabled", body.get("enabled"));
            state.put("run_state", body.getOrDefault("run_state", body.get("enabled")));
        }
        if (body.containsKey("run_state")) {
            state.put("run_state", body.get("run_state"));
        }
        if (body.containsKey("config")) {
            state.put("config", body.get("config"));
        }
        if (body.containsKey("last_signal")) {
            state.put("last_signal", body.get("last_signal"));
        }
    }
}
