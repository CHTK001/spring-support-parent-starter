package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 插件管理 Controller
 * 提供插件状态管理和重新加载接口
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/plugins")
@RequiredArgsConstructor
public class PluginsController {

    /**
     * 全局设置服务
     */
    private final SysAiGlobalSettingService globalSettingService;

    /**
     * 插件状态存储分组
     */
    private static final String PLUGIN_GROUP = "plugins";

    /**
     * 更新全局插件状态
     *
     * @param body 包含 pluginId 和 enabled 的请求体
     * @return 更新结果
     */
    @PutMapping("/global")
    public ApiResult<Boolean> updateGlobalPluginStatus(@RequestBody Map<String, Object> body) {
        String pluginId = (String) body.get("pluginId");
        Boolean enabled = (Boolean) body.get("enabled");

        log.info("更新插件状态: pluginId={}, enabled={}", pluginId, enabled);

        // 将插件开关状态保存到全局设置中
        globalSettingService.upsertValue(PLUGIN_GROUP, pluginId, String.valueOf(enabled), "插件开关状态");

        return ApiResult.ok(true);
    }

    /**
     * 重新加载插件
     *
     * @param pluginId 插件ID
     * @return 重载结果
     */
    @PostMapping("/reload/{pluginId}")
    public ApiResult<Map<String, Object>> reloadPlugin(@PathVariable String pluginId) {
        log.info("重新加载插件: pluginId={}", pluginId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("pluginId", pluginId);

        return ApiResult.ok(result);
    }

    /**
     * 获取插件列表
     *
     * @return 插件列表
     */
    @GetMapping("/list")
    public ApiResult<Map<String, Object>> getPluginList() {
        log.info("获取插件列表");

        Map<String, Object> result = new HashMap<>();
        result.put("plugins", new Object[]{});

        return ApiResult.ok(result);
    }

    /**
     * 查询插件列表
     *
     * @param body 查询参数
     * @return 插件列表
     */
    @PostMapping("/query")
    public Map<String, Object> queryPlugins(@RequestBody Map<String, Object> body) {
        log.info("查询插件列表: {}", body);

        // 从全局设置中读取 web-search 插件的开关状态
        boolean webSearchEnabled = globalSettingService.getBoolean(PLUGIN_GROUP, "web-search", false);

        Map<String, Object> webSearchPlugin = new HashMap<>();
        webSearchPlugin.put("pluginId", "web-search");
        webSearchPlugin.put("name", "网络搜索");
        webSearchPlugin.put("enabled", webSearchEnabled);
        webSearchPlugin.put("description", "AI 助手可以通过搜索获取实时信息");

        Map<String, Object> result = new HashMap<>();
        result.put("plugins", new Object[]{webSearchPlugin});

        return result;
    }
}
