package com.chua.starter.ai.support.controller.settings;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
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
* <p>本地 Provider 通过 Spring 注入的 {@link PluginOfflineProvider} Bean 加载；
* 在线市场源通过 {@link PluginMarketSourceProvider} SPI 加载（暂无数据源）。
* 开关状态持久化在 {@code sys_ai_global_setting} 的 plugins 分组。</p>
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

    private static final String PLUGINS_GROUP = "plugins";

    private final SysAiGlobalSettingService globalSettingService;
    private final List<PluginOfflineProvider> offlineProviders;

    /**
    * 获取所有 Plugins Provider 配置（本地 + 在线）。
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
                item.put("key", "local_" + key);
                item.put("providerKey", key);
                try {
                    item.put("name", provider.name());
                } catch (Exception e) {
                    item.put("name", key);
                }
                item.put("type", "local");
                item.put("enabled", isEnabled("local_" + key, true));
                localProviders.add(item);
            }
        }

        try {
            Map<String, PluginMarketSourceProvider> marketProviders = ServiceProvider.of(PluginMarketSourceProvider.class).list();
            if (marketProviders != null) {
                for (Map.Entry<String, PluginMarketSourceProvider> entry : marketProviders.entrySet()) {
                    PluginMarketSourceProvider provider = entry.getValue();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("key", "online_" + provider.source());
                    item.put("providerKey", provider.source());
                    item.put("name", provider.displayName());
                    item.put("marketType", provider.marketType());
                    item.put("url", provider.url());
                    item.put("type", "online");
                    item.put("enabled", isEnabled("online_" + provider.source(), provider.defaultEnabled()));
                    onlineProviders.add(item);
                }
            }
        } catch (Exception e) {
            log.warn("[PluginsConfig] 加载在线 PluginMarketSourceProvider 失败: {}", e.getMessage());
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
    * @param config 配置 Map
    * @return 更新后的配置
     */
    @Permission("admin")
    @Operation(summary = "更新 Plugins Provider 开关")
    @PutMapping
    public Map<String, Object> update(@RequestBody Map<String, Boolean> config) {
        if (config != null) {
            for (Map.Entry<String, Boolean> entry : config.entrySet()) {
                setEnabled(entry.getKey(), entry.getValue());
            }
        }
        return list();
    }

    private boolean isEnabled(String key, boolean defaultValue) {
        try {
            return globalSettingService.getBoolean(PLUGINS_GROUP, "provider_" + key + "_enabled", defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void setEnabled(String key, boolean enabled) {
        try {
            globalSettingService.upsertValue(PLUGINS_GROUP, "provider_" + key + "_enabled", String.valueOf(enabled), "Plugins Provider " + key + " enabled");
        } catch (Exception e) {
            log.warn("[PluginsConfig] 保存 Provider 开关失败 key={}: {}", key, e.getMessage());
        }
    }
}
