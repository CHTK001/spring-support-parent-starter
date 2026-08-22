package com.chua.starter.ai.support.nomifun.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * FS browse端点补充
 */
@Slf4j
@RestController
@RequestMapping("/api/fs")
public class ApiFsBrowseController {

    @GetMapping("/directory")
    public ApiResult<List<Map<String, Object>>> directory(@RequestParam String path) {
        List<Map<String, Object>> items = new ArrayList<>();
        // Return mock directory listing
        return ApiResult.ok(items);
    }
}
