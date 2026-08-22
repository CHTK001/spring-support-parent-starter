package com.chua.starter.ai.support.nomifun.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/extensions")
public class ApiExtensionsController {

    private final Map<String, Map<String, Object>> extensions = new ConcurrentHashMap<>();

    @GetMapping("/themes")
    public ApiResult<List<Map<String, Object>>> themes() {
        List<Map<String, Object>> themes = new ArrayList<>();
        themes.add(Map.of("id", "dark", "name", "Dark", "active", true));
        themes.add(Map.of("id", "light", "name", "Light", "active", false));
        return ApiResult.ok(themes);
    }

    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(new ArrayList<>(extensions.values()));
    }

    @GetMapping("/presets")
    public ApiResult<List<Map<String, Object>>> presets() {
        return ApiResult.ok(Collections.emptyList());
    }

    @GetMapping("/agents")
    public ApiResult<List<Map<String, Object>>> agents() {
        return ApiResult.ok(Collections.emptyList());
    }

    @GetMapping("/mcp-servers")
    public ApiResult<List<Map<String, Object>>> mcpServers() {
        return ApiResult.ok(Collections.emptyList());
    }

    @GetMapping("/skills")
    public ApiResult<List<Map<String, Object>>> skills() {
        return ApiResult.ok(Collections.emptyList());
    }

    @GetMapping("/settings-tabs")
    public ApiResult<List<Map<String, Object>>> settingsTabs() {
        List<Map<String, Object>> tabs = new ArrayList<>();
        tabs.add(Map.of("id", "general", "name", "General", "order", 1));
        tabs.add(Map.of("id", "models", "name", "Models", "order", 2));
        return ApiResult.ok(tabs);
    }

    @GetMapping("/webui")
    public ApiResult<List<Map<String, Object>>> webui() {
        return ApiResult.ok(Collections.emptyList());
    }

    @GetMapping("/agent-activity")
    public ApiResult<Map<String, Object>> agentActivity() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("active_agents", 0);
        r.put("total_executions", 0);
        return ApiResult.ok(r);
    }

    @PostMapping("/enable")
    public ApiResult<Void> enable(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @PostMapping("/disable")
    public ApiResult<Void> disable(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @GetMapping("/permissions")
    public ApiResult<Map<String, Object>> permissions() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("can_install", true);
        r.put("can_configure", true);
        return ApiResult.ok(r);
    }

    @GetMapping("/risk-level")
    public ApiResult<Map<String, Object>> riskLevel(@RequestParam String pluginId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("level", "low");
        return ApiResult.ok(r);
    }
}
