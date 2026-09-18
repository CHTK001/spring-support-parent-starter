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
* @author CH
* @since 4.0.0
 */
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/api/extensions")
public class ApiExtensionController {

    @TokenForIgnore
    @GetMapping
    /**
    * 获取加载延伸。
    * @return 获取加载延伸的结果
    */
    public Map<String, Object> getLoadedExtensions() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/themes")
    /**
    * 获取themes。
    * @return 获取themes的结果
    */
    public Map<String, Object> getThemes() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/presets")
    /**
    * 获取presets。
    * @return 获取presets的结果
    */
    public Map<String, Object> getPresets() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/agents")
    /**
    * 获取Agent。
    * @return 获取Agent的结果
    */
    public Map<String, Object> getAgents() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/mcp-servers")
    /**
    * 获取mcp服务端。
    * @return 获取mcp服务端的结果
    */
    public Map<String, Object> getMcpServers() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/skills")
    /**
    * 获取skills。
    * @return 获取skills的结果
    */
    public Map<String, Object> getSkills() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/settings-tabs")
    /**
    * 获取settingstabs。
    * @return 获取settingstabs的结果
    */
    public java.util.List<?> getSettingsTabs() {
        return List.of();
    }

    @TokenForIgnore
    @GetMapping("/webui")
    /**
    * 获取webui。
    * @return 获取webui的结果
    */
    public Map<String, Object> getWebui() {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @GetMapping("/agent-activity")
    /**
    * 获取Agentactivity。
    * @return 获取Agentactivity的结果
    */
    public Map<String, Object> getAgentActivity() {
        return Map.of("success", true, "data", Map.of());
    }

    @TokenForIgnore
    @PostMapping("/i18n")
    /**
    * 获取i18n。
    * @param body 主体
    * @return 获取i18n的结果
    */
    public Map<String, Object> getI18n(@RequestBody Map<String, String> body) {
        return Map.of("success", true, "data", Map.of());
    }

    @TokenForIgnore
    @PostMapping("/enable")
    /**
    * enable。
    * @param body 主体
    * @return enable的结果
    */
    public Map<String, Object> enable(@RequestBody Map<String, String> body) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @PostMapping("/disable")
    /**
    * disable。
    * @param body 主体
    * @return disable的结果
    */
    public Map<String, Object> disable(@RequestBody Map<String, String> body) {
        return Map.of("success", true, "data", true);
    }

    @TokenForIgnore
    @PostMapping("/permissions")
    /**
    * 获取权限。
    * @param body 主体
    * @return 获取权限的结果
    */
    public Map<String, Object> getPermissions(@RequestBody Map<String, String> body) {
        return Map.of("success", true, "data", List.of());
    }

    @TokenForIgnore
    @PostMapping("/risk-level")
    /**
    * 获取risk水平仪。
    * @param body 主体
    * @return 获取risk水平仪的结果
    */
    public Map<String, Object> getRiskLevel(@RequestBody Map<String, String> body) {
        return Map.of("success", true, "data", "low");
    }
}
