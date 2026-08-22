package com.chua.starter.ai.support.nomifun.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 创意工坊控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/workshop")
public class ApiWorkshopController {

    private final Map<String, Map<String, Object>> canvases = new ConcurrentHashMap<>();

    @GetMapping("/canvases")
    public ApiResult<List<Map<String, Object>>> canvases() {
        return ApiResult.ok(new ArrayList<>(canvases.values()));
    }

    @PostMapping("/canvases")
    public ApiResult<Map<String, Object>> createCanvas(@RequestBody Map<String, Object> body) {
        String id = "canvas-" + System.currentTimeMillis();
        Map<String, Object> canvas = new LinkedHashMap<>();
        canvas.put("canvas_id", id);
        canvas.put("name", body.get("name"));
        canvas.put("content", body.getOrDefault("content", "{}"));
        canvases.put(id, canvas);
        return ApiResult.ok(canvas);
    }

    @PutMapping("/canvases/{canvasId}")
    public ApiResult<Void> updateCanvas(@PathVariable String canvasId, @RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @DeleteMapping("/canvases/{canvasId}")
    public ApiResult<Void> deleteCanvas(@PathVariable String canvasId) {
        canvases.remove(canvasId);
        return ApiResult.ok();
    }

    @PostMapping("/canvases/{canvasId}/rename")
    public ApiResult<Void> renameCanvas(@PathVariable String canvasId, @RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @GetMapping("/assets")
    public ApiResult<List<Map<String, Object>>> assets() {
        return ApiResult.ok(Collections.emptyList());
    }

    @GetMapping("/collections")
    public ApiResult<List<Map<String, Object>>> collections() {
        return ApiResult.ok(Collections.emptyList());
    }

    @PostMapping("/collections/rename")
    public ApiResult<Void> renameCollection(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @GetMapping("/tasks")
    public ApiResult<List<Map<String, Object>>> tasks() {
        return ApiResult.ok(Collections.emptyList());
    }
}
