package com.chua.starter.ai.support.manage.api;

import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 终端管理端点 Stub。
 * 桌面模式下终端由 Electron 管理，WebUI 模式返回空列表。
 */
@RestController
@RequestMapping("/api/terminals")
public class ApiTerminalController {

    @TokenForIgnore
    @GetMapping
    public Map<String, Object> list() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        return Map.of("success", true, "data", Map.of(
                "terminal_id", "term-" + System.currentTimeMillis(),
                "status", "running"
        ));
    }

    @TokenForIgnore
    @GetMapping("/{terminalId}")
    public Map<String, Object> get(@PathVariable String terminalId) {
        return Map.of("success", true, "data", Map.of(
                "terminal_id", terminalId,
                "status", "running"
        ));
    }

    @TokenForIgnore
    @PostMapping("/{terminalId}/input")
    public Map<String, Object> input(@PathVariable String terminalId, @RequestBody Map<String, String> body) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @PostMapping("/{terminalId}/resize")
    public Map<String, Object> resize(@PathVariable String terminalId, @RequestBody Map<String, Integer> body) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @PostMapping("/{terminalId}/kill")
    public Map<String, Object> kill(@PathVariable String terminalId) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @PostMapping("/{terminalId}/relaunch")
    public Map<String, Object> relaunch(@PathVariable String terminalId) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @PostMapping("/{terminalId}/relaunch-shell")
    public Map<String, Object> relaunchShell(@PathVariable String terminalId) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @DeleteMapping("/{terminalId}")
    public Map<String, Object> delete(@PathVariable String terminalId) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @GetMapping("/mcp-register-template")
    public Map<String, Object> mcpRegisterTemplate() {
        return Map.of("success", true, "data", Map.of());
    }

    @TokenForIgnore
    @PostMapping("/register-knowledge")
    public Map<String, Object> registerKnowledge(@RequestBody Map<String, Object> body) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @PostMapping("/register-knowledge-global")
    public Map<String, Object> registerKnowledgeGlobal(@RequestBody Map<String, Object> body) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @PostMapping("/unregister-knowledge-global")
    public Map<String, Object> unregisterKnowledgeGlobal(@RequestBody Map<String, Object> body) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @GetMapping("/knowledge-global-status")
    public Map<String, Object> knowledgeGlobalStatus() {
        return Map.of("success", true, "data", Map.of("registered", false));
    }

    @TokenForIgnore
    @GetMapping("/{terminalId}/workspace")
    public Map<String, Object> workspace(@PathVariable String terminalId,
                                          @RequestParam(defaultValue = ".") String path,
                                          @RequestParam(required = false) String search) {
        return Map.of("success", true, "data", List.of());
    }
}
