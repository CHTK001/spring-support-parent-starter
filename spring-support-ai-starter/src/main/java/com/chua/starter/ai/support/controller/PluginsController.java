package com.chua.starter.ai.support.controller;

import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.service.AiPluginCatalogService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.impl.CustomLocalToolRegistrar;
import com.chua.starter.common.support.annotations.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * 插件清单服务
     */
    private final AiPluginCatalogService pluginCatalogService;

    /**
     * 自定义本地工具注册器
     */
    private final CustomLocalToolRegistrar customToolRegistrar;

    /**
     * 插件状态存储分组
     */
    private static final String PLUGIN_GROUP = "plugins";

    /**
     * 更新全局插件状态
     *
     * @param body 包含 pluginId 和 enabled 的请求体
     * @return 是否写入成功
     */
    @Permission("admin")
    @PutMapping("/global")
    public ApiResult<Boolean> updateGlobalPluginStatus(@RequestBody Map<String, Object> body) {
        String pluginId = body == null ? null : (String) body.get("pluginId");
        Boolean enabled = body == null ? null : (Boolean) body.get("enabled");
        if (pluginId == null || pluginId.isBlank() || enabled == null) {
            log.warn("更新插件状态参数不完整: pluginId={}, enabled={}", pluginId, enabled);
            return ApiResult.ok(false);
        }

        log.info("更新插件状态: pluginId={}, enabled={}", pluginId, enabled);

        // 将插件开关状态保存到全局设置中
        globalSettingService.upsertValue(PLUGIN_GROUP, pluginId, String.valueOf(enabled), "插件开关状态");

        return ApiResult.ok(true);
    }

    /**
     * 重新加载插件
     *
     * <p>作废本地插件扫描缓存并重新读取磁盘目录，返回该插件重载后是否仍然存在。</p>
     *
     * @param pluginId 插件ID
     * @return 重载结果
     */
    @Permission("admin")
    @PostMapping("/reload/{pluginId}")
    public ApiResult<Map<String, Object>> reloadPlugin(@PathVariable String pluginId) {
        log.info("重新加载插件: pluginId={}", pluginId);

        pluginCatalogService.refresh();
        Map<String, Object> plugin = pluginCatalogService.findLocalPlugin(pluginId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pluginId", pluginId);
        result.put("refreshed", true);
        result.put("found", plugin != null);
        result.put("plugin", plugin);

        return ApiResult.ok(result);
    }

    /**
     * 获取插件列表
     *
     * <p>返回本地已安装插件，来源由 {@code plugins} 分组的 Provider 开关决定，
     * 每条记录附带 {@code enabled} 表示该插件自身的开关状态。</p>
     *
     * @return 插件列表
     */
    @GetMapping("/list")
    public ApiResult<Map<String, Object>> getPluginList() {
        List<Map<String, Object>> plugins = pluginCatalogService.listLocalPlugins();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plugins", plugins);
        result.put("count", plugins.size());

        return ApiResult.ok(result);
    }

    /**
     * 查询插件列表
     *
     * <p>{@code keyword} 为空时返回全部本地插件；{@code market} 为真时额外在启用的在线
     * 市场源中执行真实检索。单个市场源不可用只影响该源结果，不使整体请求失败。</p>
     *
     * @param body 查询参数（keyword、market）
     * @return 插件列表
     */
    @PostMapping("/query")
    public Map<String, Object> queryPlugins(@RequestBody(required = false) Map<String, Object> body) {
        String keyword = body == null ? null : Objects.toString(body.get("keyword"), null);
        boolean market = body != null && Boolean.parseBoolean(Objects.toString(body.get("market"), "false"));
        log.info("查询插件列表: keyword={}, market={}", keyword, market);

        List<Map<String, Object>> local = filterByKeyword(pluginCatalogService.listLocalPlugins(), keyword);
        List<Map<String, Object>> remote = market ? pluginCatalogService.searchMarketPlugins(keyword) : List.of();

        List<Map<String, Object>> plugins = new ArrayList<>(local);
        plugins.addAll(remote);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plugins", plugins);
        result.put("localCount", local.size());
        result.put("marketCount", remote.size());

        return result;
    }

    /**
     * 注册自定义本地工具目录（管理员）。
     *
     * <p>请求体 <code>{name, path}</code>：path 为本机含 {@code plugin.json} 或
     * {@code package.json} 的目录；目录自身含清单则注册为单个工具，否则遍历其一级子目录。</p>
     *
     * @param body 注册参数
     * @return 注册结果与识别出的工具
     */
    @Permission("admin")
    @PostMapping("/custom")
    public ApiResult<Map<String, Object>> addCustomTool(@RequestBody Map<String, Object> body) {
        String name = body == null ? null : Objects.toString(body.get("name"), null);
        String path = body == null ? null : Objects.toString(body.get("path"), null);
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> tools = customToolRegistrar.register(name, path);
            // 注册项变更后作废快照缓存，保证列表立即反映
            pluginCatalogService.refresh();
            result.put("success", true);
            result.put("tools", tools);
            result.put("count", tools.size());
            return ApiResult.ok(result);
        } catch (Exception e) {
            log.warn("注册自定义工具失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
            return ApiResult.ok(result);
        }
    }

    /**
     * 移除自定义本地工具（管理员）。请求体 <code>{pluginId}</code>，
     * 仅对 {@code custom:} 前缀的自定义工具生效（id 含斜杠，故走请求体而非路径变量）。
     *
     * @param body {pluginId}
     * @return 移除结果
     */
    @Permission("admin")
    @DeleteMapping("/custom")
    public ApiResult<Map<String, Object>> removeCustomTool(@RequestBody Map<String, Object> body) {
        String pluginId = body == null ? null : Objects.toString(body.get("pluginId"), null);
        Map<String, Object> result = new LinkedHashMap<>();
        if (!customToolRegistrar.isCustom(pluginId)) {
            result.put("success", false);
            result.put("message", "仅支持移除自定义工具");
            return ApiResult.ok(result);
        }
        boolean removed = customToolRegistrar.unregister(pluginId);
        pluginCatalogService.refresh();
        result.put("success", removed);
        result.put("pluginId", pluginId);
        return ApiResult.ok(result);
    }

    /**
     * 按关键词过滤插件条目。
     *
     * @param plugins 待过滤的插件条目，允许为 null
     * @param keyword 关键词，为空时不过滤
     * @return 命中的插件条目列表
     */
    private List<Map<String, Object>> filterByKeyword(List<Map<String, Object>> plugins, String keyword) {
        if (plugins == null) {
            return List.of();
        }
        if (keyword == null || keyword.isBlank()) {
            return plugins;
        }
        String lower = keyword.toLowerCase();
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> plugin : plugins) {
            if (containsLower(plugin.get("name"), lower) || containsLower(plugin.get("description"), lower)
                    || containsLower(plugin.get("id"), lower)) {
                matched.add(plugin);
            }
        }
        return matched;
    }

    /**
     * 判断字段值是否包含关键词。
     *
     * @param value  字段值，可为 null
     * @param lower  已转小写的关键词
     * @return 是否命中
     */
    private boolean containsLower(Object value, String lower) {
        return value != null && String.valueOf(value).toLowerCase().contains(lower);
    }
}
