package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 扩展贡献端点 Stub。
 * 前端在启动时会请求这些端点，返回空数组即可。
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/extensions")
public class ApiExtensionController {

    @TokenForIgnore
    @GetMapping
    public Map<String, Object> getLoadedExtensions() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/themes")
    public Map<String, Object> getThemes() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/presets")
    public Map<String, Object> getPresets() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/agents")
    public Map<String, Object> getAgents() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/mcp-servers")
    public Map<String, Object> getMcpServers() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/skills")
    public Map<String, Object> getSkills() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/settings-tabs")
    public java.util.List<?> getSettingsTabs() {
        return List.of();
    }

    @TokenForIgnore
    @GetMapping("/webui")
    public Map<String, Object> getWebui() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/agent-activity")
    public Map<String, Object> getAgentActivity() {
        return Map.of("success", true, "data", Map.of());
    }

    @TokenForIgnore
    @PostMapping("/i18n")
    public Map<String, Object> getI18n(@RequestBody Map<String, String> body) {
        return Map.of("success", true, "data", Map.of());
    }

    @TokenForIgnore
    @PostMapping("/enable")
    public Map<String, Object> enable(@RequestBody Map<String, String> body) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @PostMapping("/disable")
    public Map<String, Object> disable(@RequestBody Map<String, String> body) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @PostMapping("/permissions")
    public Map<String, Object> getPermissions(@RequestBody Map<String, String> body) {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @PostMapping("/risk-level")
    public Map<String, Object> getRiskLevel(@RequestBody Map<String, String> body) {
        return Map.of("success", true, "data", "low");
    }
}
