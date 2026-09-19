package com.chua.starter.ai.support.controller.settings;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.service.AiPluginCatalogService;
import com.chua.starter.ai.support.spi.PluginMarketSourceProvider;
import com.chua.starter.ai.support.spi.PluginOfflineProvider;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plugins 配置控制器，枚举本地/在线 PluginProvider 并支持开关。
 *
 * <p>本地 Provider 通过 Spring 注入的 {@link PluginOfflineProvider} Bean 加载，
 * 其中 {@code DatasearchPluginOfflineProvider} 会真实扫描本机编辑器/Agent 的插件目录；
 * 在线市场源通过 {@link PluginMarketSourceProvider} SPI 加载，检索请求由
 * {@link AiPluginCatalogService} 委派到 datasearch 的在线插件来源执行。</p>
 *
 * <p>开关状态持久化在 {@code sys_ai_global_setting} 的 {@code plugins} 分组，
 * 字段格式统一由 {@link AiPluginCatalogService} 保管；此处关闭某 Provider 后，
 * 插件清单接口不再返回该来源的插件。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/plugins-config")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - Plugins 配置")
public class AiPluginConfigController {

    private final AiPluginCatalogService pluginCatalogService;
    private final List<PluginOfflineProvider> offlineProviders;

    /**
     * 获取所有 Plugins Provider 配置（本地 + 在线）。
     *
     * <p>本地条目附带 {@code pluginCount}，取自插件清单服务的扫描快照，
     * 用于让开关页显示该来源实际能提供多少插件。</p>
     *
     * @return Provider 列表
     */
    @Permission("admin")
    @Operation(summary = "获取 Plugins Provider 配置列表")
    @GetMapping
    public Map<String, Object> list() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> localProviders = new ArrayList<>();
        List<Map<String, Object>> onlineProviders = new ArrayList<>();

        if (offlineProviders != null) {
            for (PluginOfflineProvider provider : offlineProviders) {
                Map<String, Object> item = new LinkedHashMap<>();
                String key = provider.getClass().getSimpleName();
                String providerKey = "local_" + key;
                boolean enabled = pluginCatalogService.isProviderEnabled(providerKey, true);
                item.put("key", providerKey);
                item.put("providerKey", key);
                item.put("name", providerName(provider, key));
                item.put("type", "local");
                item.put("enabled", enabled);
                item.put("pluginCount", enabled ? pluginCatalogService.countLocalPlugins(providerKey) : 0);
                localProviders.add(item);
            }
        }

        for (Map.Entry<String, PluginMarketSourceProvider> entry : loadMarketProviders().entrySet()) {
            PluginMarketSourceProvider provider = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            String providerKey = "online_" + provider.source();
            boolean enabled = pluginCatalogService.isProviderEnabled(providerKey, provider.defaultEnabled());
            item.put("key", providerKey);
            item.put("providerKey", provider.source());
            item.put("name", provider.displayName());
            item.put("marketType", provider.marketType());
            item.put("url", provider.url());
            item.put("type", "online");
            item.put("enabled", enabled);
            onlineProviders.add(item);
        }

        result.put("localProviders", localProviders);
        result.put("onlineProviders", onlineProviders);
        result.put("localCount", localProviders.size());
        result.put("onlineCount", onlineProviders.size());
        return result;
    }

    /**
     * 更新 Plugins Provider 开关状态。
     *
     * @param config 键为 Provider 键（{@code local_}/{@code online_} 前缀）、值为开关状态的 Map，可为 null
     * @return 更新后的配置
     */
    @Permission("admin")
    @Operation(summary = "更新 Plugins Provider 开关")
    @PutMapping
    public Map<String, Object> update(@RequestBody Map<String, Boolean> config) {
        if (config != null) {
            for (Map.Entry<String, Boolean> entry : config.entrySet()) {
                if (entry.getValue() != null) {
                    pluginCatalogService.setProviderEnabled(entry.getKey(), entry.getValue());
                }
            }
        }
        return list();
    }

    /**
     * 加载在线插件市场源 SPI 实现。
     *
     * @return 源键 → 市场源实现，加载失败时返回空映射
     */
    private Map<String, PluginMarketSourceProvider> loadMarketProviders() {
        try {
            Map<String, PluginMarketSourceProvider> providers = ServiceProvider.of(PluginMarketSourceProvider.class).list();
            return providers != null ? providers : Map.of();
        } catch (Exception e) {
            log.warn("[PluginsConfig] 加载在线 PluginMarketSourceProvider 失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 取本地 Provider 展示名称。
     *
     * @param provider 本地插件来源，不允许为 null
     * @param fallback 取名失败时的兜底标识
     * @return 展示名称
     */
    private String providerName(PluginOfflineProvider provider, String fallback) {
        try {
            return provider.name();
        } catch (Exception e) {
            return fallback;
        }
    }
}
