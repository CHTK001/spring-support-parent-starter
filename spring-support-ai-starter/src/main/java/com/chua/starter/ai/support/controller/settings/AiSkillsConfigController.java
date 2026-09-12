package com.chua.starter.ai.support.controller.settings;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.manage.api.market.SkillMarketSourceProvider;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.spi.SkillOfflineProvider;
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
 * Skills 配置控制器，枚举本地/在线 SkillProvider 并支持开关。
 *
 * <p>本地 Provider 通过 {@link SkillOfflineProvider} SPI 加载；
 * 在线市场源通过 {@link SkillMarketSourceProvider} SPI 加载。
 * 开关状态持久化在 {@code sys_ai_global_setting} 的 skills 分组。</p>
 *
 * @since 4.0.0
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/skills-config")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - Skills 配置")
public class AiSkillsConfigController {

    private static final String SKILLS_GROUP = "skills";

    private final SysAiGlobalSettingService globalSettingService;

    /**
     * 获取所有 Skills Provider 配置（本地 + 在线）。
     *
     * @return Provider 列表，每项含 key/name/type/enabled
     */
    @Permission("admin")
    @Operation(summary = "获取 Skills Provider 配置列表")
    @GetMapping
    public Map<String, Object> list() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> localProviders = new ArrayList<>();
        List<Map<String, Object>> onlineProviders = new ArrayList<>();

        // 本地 SkillOfflineProvider
        try {
            Map<String, SkillOfflineProvider> offlineProviders = ServiceProvider.of(SkillOfflineProvider.class).list();
            if (offlineProviders != null) {
                for (Map.Entry<String, SkillOfflineProvider> entry : offlineProviders.entrySet()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("key", "local_" + entry.getKey());
                    item.put("providerKey", entry.getKey());
                    try {
                        item.put("name", entry.getValue().name());
                    } catch (Exception e) {
                        item.put("name", entry.getKey());
                    }
                    item.put("type", "local");
                    item.put("enabled", isEnabled("local_" + entry.getKey(), true));
                    localProviders.add(item);
                }
            }
        } catch (Exception e) {
            log.warn("[SkillsConfig] 加载本地 SkillOfflineProvider 失败: {}", e.getMessage());
        }

        // 在线 SkillMarketSourceProvider
        try {
            Map<String, SkillMarketSourceProvider> marketProviders = ServiceProvider.of(SkillMarketSourceProvider.class).list();
            if (marketProviders != null) {
                for (Map.Entry<String, SkillMarketSourceProvider> entry : marketProviders.entrySet()) {
                    SkillMarketSourceProvider provider = entry.getValue();
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
            log.warn("[SkillsConfig] 加载在线 SkillMarketSourceProvider 失败: {}", e.getMessage());
        }

        result.put("localProviders", localProviders);
        result.put("onlineProviders", onlineProviders);
        result.put("localCount", localProviders.size());
        result.put("onlineCount", onlineProviders.size());
        return result;
    }

    /**
     * 更新 Skills Provider 开关状态。
     *
     * @param config 配置 Map，key 为 provider key，value 为 boolean
     * @return 更新后的配置
     */
    @Permission("admin")
    @Operation(summary = "更新 Skills Provider 开关")
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
            return globalSettingService.getBoolean(SKILLS_GROUP, "provider_" + key + "_enabled", defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void setEnabled(String key, boolean enabled) {
        try {
            globalSettingService.upsertValue(SKILLS_GROUP, "provider_" + key + "_enabled", String.valueOf(enabled), "Skills Provider " + key + " enabled");
        } catch (Exception e) {
            log.warn("[SkillsConfig] 保存 Provider 开关失败 key={}: {}", key, e.getMessage());
        }
    }
}
