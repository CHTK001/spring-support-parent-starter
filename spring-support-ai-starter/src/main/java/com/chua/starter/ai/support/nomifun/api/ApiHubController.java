package com.chua.starter.ai.support.nomifun.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hub控制器（扩展市场）
 */
@Slf4j
@RestController
@RequestMapping("/api/hub")
public class ApiHubController {

    @GetMapping("/extensions")
    public ApiResult<List<Map<String, Object>>> extensions() {
        return ApiResult.ok(Collections.emptyList());
    }

    @PostMapping("/install")
    public ApiResult<Map<String, Object>> install(@RequestBody Map<String, Object> body) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("installed", true);
        return ApiResult.ok(r);
    }

    @PostMapping("/uninstall")
    public ApiResult<Void> uninstall(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @PostMapping("/retry-install")
    public ApiResult<Void> retryInstall(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @GetMapping("/check-updates")
    public ApiResult<Map<String, Object>> checkUpdates() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("has_updates", false);
        return ApiResult.ok(r);
    }

    @PostMapping("/update")
    public ApiResult<Void> update(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }
}
