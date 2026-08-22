package com.chua.starter.ai.support.nomifun.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 文件预览控制器（PPT/Word/Excel）
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class ApiPreviewController {

    @PostMapping("/ppt-preview/start")
    public ApiResult<Map<String, Object>> pptStart(@RequestBody Map<String, Object> body) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("port", 0);
        r.put("status", "started");
        return ApiResult.ok(r);
    }

    @PostMapping("/ppt-preview/stop")
    public ApiResult<Void> pptStop() {
        return ApiResult.ok();
    }

    @PostMapping("/word-preview/start")
    public ApiResult<Map<String, Object>> wordStart(@RequestBody Map<String, Object> body) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("port", 0);
        r.put("status", "started");
        return ApiResult.ok(r);
    }

    @PostMapping("/word-preview/stop")
    public ApiResult<Void> wordStop() {
        return ApiResult.ok();
    }

    @PostMapping("/excel-preview/start")
    public ApiResult<Map<String, Object>> excelStart(@RequestBody Map<String, Object> body) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("port", 0);
        r.put("status", "started");
        return ApiResult.ok(r);
    }

    @PostMapping("/excel-preview/stop")
    public ApiResult<Void> excelStop() {
        return ApiResult.ok();
    }

    @PostMapping("/star-office/detect")
    public ApiResult<Map<String, Object>> detect() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("detected", false);
        r.put("path", "");
        return ApiResult.ok(r);
    }
}
