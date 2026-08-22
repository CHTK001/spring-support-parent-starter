package com.chua.starter.ai.support.nomifun.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 浏览器控制控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/browser")
public class ApiBrowserController {

    private final Map<String, Map<String, Object>> lanes = new ConcurrentHashMap<>();

    @GetMapping("/overview")
    public ApiResult<Map<String, Object>> overview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("running_lanes", lanes.size());
        overview.put("supported", true);
        overview.put("enabled", false);
        return ApiResult.ok(overview);
    }

    @GetMapping("/lanes")
    public ApiResult<List<Map<String, Object>>> lanes() {
        return ApiResult.ok(new ArrayList<>(lanes.values()));
    }

    @PostMapping("/lanes/{laneId}/foreground")
    public ApiResult<Void> foreground(@PathVariable String laneId) {
        return ApiResult.ok();
    }

    @PostMapping("/lanes/{laneId}/background")
    public ApiResult<Void> background(@PathVariable String laneId) {
        return ApiResult.ok();
    }

    @PostMapping("/lanes/{laneId}/close")
    public ApiResult<Void> close(@PathVariable String laneId) {
        lanes.remove(laneId);
        return ApiResult.ok();
    }

    @PostMapping("/close-all")
    public ApiResult<Map<String, Object>> closeAll() {
        lanes.clear();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("closed", 0);
        return ApiResult.ok(r);
    }

    @GetMapping("/display-mode")
    public ApiResult<Map<String, Object>> displayMode() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("display_mode", "headless");
        m.put("effective_visibility", "headless");
        return ApiResult.ok(m);
    }

    @PostMapping("/display-mode")
    public ApiResult<Void> setDisplayMode(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @GetMapping("/resource-policy")
    public ApiResult<Map<String, Object>> resourcePolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("max_concurrent_tabs", 5);
        policy.put("max_memory_mb", 512);
        return ApiResult.ok(policy);
    }

    @GetMapping("/login/open")
    public ApiResult<Map<String, Object>> loginOpen() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "not_started");
        return ApiResult.ok(r);
    }

    @PostMapping("/login/close")
    public ApiResult<Void> loginClose() {
        return ApiResult.ok();
    }

    @GetMapping("/login/status")
    public ApiResult<Map<String, Object>> loginStatus() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("authenticated", false);
        return ApiResult.ok(r);
    }
}
