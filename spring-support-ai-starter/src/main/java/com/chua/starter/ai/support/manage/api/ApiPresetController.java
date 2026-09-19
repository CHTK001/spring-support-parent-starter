package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预设配置控制器。
 *
 * <p>提供预设（Preset）的 CRUD、状态切换、解析和批量导入功能。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/presets")
@RequiredArgsConstructor
public class ApiPresetController {

    private final SysAiConversationStore conversationStore; // conversation存储

    /**
     * 内存预设存储：preset_标识 → 预设数据
     */
    private final Map<String, Map<String, Object>> presets = new ConcurrentHashMap<>();

    @GetMapping
    /**
     * 列表。
     * @return 列表的结果
     */
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(new ArrayList<>(presets.values()));
    }

    @GetMapping("/{presetId}")
    /**
     * 获取。
     * @param presetId presetid
     * @return 获取的结果
     */
    public ApiResult<Map<String, Object>> get(@PathVariable String presetId) {
        Map<String, Object> preset = presets.get(presetId);
        if (preset == null) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param presetId presetid
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "预设不存在: " + presetId);
        }
        return ApiResult.ok(preset);
    }

    @PostMapping
    /**
     * 创建。
     * @param body 主体
     * @return 创建的结果
     */
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String presetId = String.valueOf(body.getOrDefault("preset_id", conversationStore.newId()));
        body.put("preset_id", presetId);
        body.putIfAbsent("source", "user");
        body.putIfAbsent("target", "conversation");
        body.putIfAbsent("enabled", true);
        body.putIfAbsent("created_at", System.currentTimeMillis());
        body.putIfAbsent("updated_at", System.currentTimeMillis());
        body.putIfAbsent("audience_tag_ids", new ArrayList<>());
        body.putIfAbsent("scenario_tag_ids", new ArrayList<>());
        presets.put(presetId, body);
        return ApiResult.ok(body);
    }

    @PutMapping("/{presetId}")
    public ApiResult<Map<String, Object>> update(@PathVariable String presetId,
                                                  @RequestBody Map<String, Object> body) {
        Map<String, Object> preset = presets.get(presetId);
        if (preset == null) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param presetId presetid
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "预设不存在: " + presetId);
        }
        body.forEach((k, v) -> {
            if (!"preset_id".equals(k)) {
                preset.put(k, v);
            }
        });
        preset.put("updated_at", System.currentTimeMillis());
        return ApiResult.ok(preset);
    }

    @DeleteMapping("/{presetId}")
    /**
     * 删除。
     * @param presetId presetid
     * @return 删除的结果
     */
    public ApiResult<Void> delete(@PathVariable String presetId) {
        Map<String, Object> preset = presets.remove(presetId);
        if (preset == null) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param presetId presetid
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "预设不存在: " + presetId);
        }
        return ApiResult.ok();
    }

    @PatchMapping("/{presetId}/state")
    public ApiResult<Map<String, Object>> setState(@PathVariable String presetId,
                                                    @RequestBody Map<String, Object> body) {
        Map<String, Object> preset = presets.get(presetId);
        if (preset == null) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param presetId presetid
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "预设不存在: " + presetId);
        }
        if (body.containsKey("enabled")) {
            preset.put("enabled", body.get("enabled"));
        }
        preset.put("updated_at", System.currentTimeMillis());
        return ApiResult.ok(preset);
    }

    @PostMapping("/{presetId}/resolve")
    public ApiResult<Map<String, Object>> resolve(@PathVariable String presetId,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> preset = presets.get(presetId);
        if (preset == null) {
            /**
             * api异常。
             * @param ApiErrorCode.NOT_FOUND api错误编码.notfound
             * @param presetId presetid
             * @return api异常的结果
             */
            throw new ApiException(ApiErrorCode.NOT_FOUND, "预设不存在: " + presetId);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>(preset);
        snapshot.put("resolved_at", System.currentTimeMillis());
        return ApiResult.ok(snapshot);
    }

    @PostMapping("/import")
    /**
     * 导入presets。
     * @param body 主体
     * @return 导入presets的结果
     */
    public ApiResult<Map<String, Object>> importPresets(@RequestBody Map<String, Object> body) {
        Object rawPresets = body.get("presets");
        int imported = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        if (rawPresets instanceof List) {
            for (Object item : (List<?>) rawPresets) {
                if (item instanceof Map) {
                    Map<String, Object> p = new LinkedHashMap<>((Map<String, Object>) item);
                    String id = String.valueOf(p.getOrDefault("preset_id", conversationStore.newId()));
                    p.put("preset_id", id);
                    p.putIfAbsent("source", "user");
                    p.putIfAbsent("created_at", System.currentTimeMillis());
                    presets.put(id, p);
                    imported++;
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("errors", errors);
        return ApiResult.ok(result);
    }
}
