package com.chua.starter.ai.support.nomifun.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 终端管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/terminals")
public class ApiTerminalController {

    private final Map<String, Map<String, Object>> terminals = new ConcurrentHashMap<>();
    private int counter = 0;

    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(new ArrayList<>(terminals.values()));
    }

    @GetMapping("/{terminalId}")
    public ApiResult<Map<String, Object>> get(@PathVariable String terminalId) {
        return ApiResult.ok(terminals.get(terminalId));
    }

    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String id = "term-" + (++counter);
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("terminal_id", id);
        t.put("name", body.getOrDefault("name", "Terminal " + id));
        t.put("cwd", body.getOrDefault("cwd", System.getProperty("user.dir")));
        t.put("status", "running");
        t.put("created_at", System.currentTimeMillis());
        terminals.put(id, t);
        log.info("Terminal created: {}", id);
        return ApiResult.ok(t);
    }

    @DeleteMapping("/{terminalId}")
    public ApiResult<Void> delete(@PathVariable String terminalId) {
        terminals.remove terminalId);
        return ApiResult.ok();
    }

    @PostMapping("/{terminalId}/input")
    public ApiResult<Void> input(@PathVariable String terminalId, @RequestBody Map<String, Object> body) {
        terminals.getOrDefault(terminalId, new LinkedHashMap<>()).put("last_input", body);
        return ApiResult.ok();
    }

    @PostMapping("/{terminalId}/resize")
    public ApiResult<Void> resize(@PathVariable String terminalId, @RequestBody Map<String, Object> body) {
        return ApiResult.ok();
    }

    @PostMapping("/{terminalId}/kill")
    public ApiResult<Void> kill(@PathVariable String terminalId) {
        return ApiResult.ok();
    }

    @GetMapping("/mcp-register-template")
    public ApiResult<Map<String, Object>> mcpRegisterTemplate() {
        Map<String, Object> tmpl = new LinkedHashMap<>();
        tmpl.put("command", "npx");
        tmpl.put("args", Arrays.asList("@modelcontextprotocol/server-filesystem", "/workspace"));
        return ApiResult.ok(tmpl);
    }

    @GetMapping("/knowledge-global-status")
    public ApiResult<Map<String, Object>> knowledgeGlobalStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", false);
        status.put("vector_store", "none");
        return ApiResult.ok(status);
    }
}
