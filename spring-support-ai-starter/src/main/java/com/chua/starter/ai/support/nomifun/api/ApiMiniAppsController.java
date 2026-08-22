package com.chua.starter.ai.support.nomifun.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MiniApps控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/miniapps")
public class ApiMiniAppsController {

    private final Map<String, Map<String, Object>> miniapps = new ConcurrentHashMap<>();

    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(new ArrayList<>(miniapps.values()));
    }

    @GetMapping("/{miniappId}")
    public ApiResult<Map<String, Object>> get(@PathVariable String miniappId) {
        return ApiResult.ok(miniapps.get(miniappId));
    }

    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String id = "ma-" + System.currentTimeMillis();
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("miniapp_id", id);
        app.put("name", body.get("name"));
        miniapps.put(id, app);
        return ApiResult.ok(app);
    }

    @PutMapping("/{miniappId}")
    public ApiResult<Void> update(@PathVariable String miniappId, @RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @DeleteMapping("/{miniappId}")
    public ApiResult<Void> delete(@PathVariable String miniappId) {
        miniapps.remove(miniappId);
        return ApiResult.ok();
    }

    @PostMapping("/validate")
    public ApiResult<Map<String, Object>> validate(@RequestBody Map<String, Object> body) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("valid", true);
        r.put("findings", Collections.emptyList());
        return ApiResult.ok(r);
    }

    @PostMapping("/import")
    public ApiResult<Map<String, Object>> import_(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(Map.of("miniapp_id", "imported-" + System.currentTimeMillis()));
    }
}
