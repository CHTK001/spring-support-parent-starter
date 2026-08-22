package com.chua.starter.ai.support.nomifun.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预览历史控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/preview-history")
public class ApiPreviewHistoryController {

    private final Map<String, Map<String, Object>> history = new ConcurrentHashMap<>();

    @GetMapping("/list")
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(new ArrayList<>(history.values()));
    }

    @PostMapping("/save")
    public ApiResult<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        String id = "snap-" + System.currentTimeMillis();
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("snapshot_id", id);
        snap.put("target", body.get("target"));
        snap.put("created_at", System.currentTimeMillis());
        history.put(id, snap);
        return ApiResult.ok(snap);
    }

    @PostMapping("/get-content")
    public ApiResult<Map<String, Object>> getContent(@RequestBody Map<String, Object> body) {
        String id = (String) body.get("snapshot_id");
        Map<String, Object> snap = history.get(id);
        if (snap == null) return ApiResult.error("not found");
        return ApiResult.ok(snap);
    }
}
