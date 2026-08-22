package com.chua.starter.ai.support.nomifun.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 渠道管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/channel")
public class ApiChannelController {

    @GetMapping("/plugins")
    public ApiResult<List<Map<String, Object>>> plugins() {
        return ApiResult.ok(Collections.emptyList());
    }

    @PostMapping("/plugins/enable")
    public ApiResult<Void> enablePlugin(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @PostMapping("/plugins/disable")
    public ApiResult<Void> disablePlugin(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @PostMapping("/plugins/delete")
    public ApiResult<Void> deletePlugin(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @PostMapping("/plugins/test")
    public ApiResult<Map<String, Object>> testPlugin(@RequestBody Map<String, Object> body) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        return ApiResult.ok(r);
    }

    @GetMapping("/pairings")
    public ApiResult<List<Map<String, Object>>> pairings() {
        return ApiResult.ok(Collections.emptyList());
    }

    @PostMapping("/pairings/approve")
    public ApiResult<Void> approvePairing(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @PostMapping("/pairings/reject")
    public ApiResult<Void> rejectPairing(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @GetMapping("/users")
    public ApiResult<List<Map<String, Object>>> users() {
        return ApiResult.ok(Collections.emptyList());
    }

    @PostMapping("/users/revoke")
    public ApiResult<Void> revokeUser(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @PostMapping("/settings/group-access")
    public ApiResult<Void> setGroupAccess(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @GetMapping("/sessions")
    public ApiResult<List<Map<String, Object>>> sessions() {
        return ApiResult.ok(Collections.emptyList());
    }

    @PostMapping("/settings/sync")
    public ApiResult<Void> syncSettings(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @PostMapping("/settings/companion")
    public ApiResult<Void> setCompanion(@RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @PostMapping("/weixin/login/start")
    public ApiResult<Map<String, Object>> weixinLoginStart() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("qr_url", "");
        r.put("status", "pending");
        return ApiResult.ok(r);
    }
}
