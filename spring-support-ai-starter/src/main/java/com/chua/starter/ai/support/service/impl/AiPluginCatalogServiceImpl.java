package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.service.AiPluginCatalogService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.spi.PluginMarketSourceProvider;
import com.chua.starter.ai.support.spi.PluginOfflineProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 插件清单服务实现。
 *
 * <p>本地插件扫描会真实读取磁盘目录，代价较高，因此按 60 秒缓存一份「Provider 键 → 插件列表」快照，
 * {@link #refresh()} 可立即作废该快照；在线市场检索是实时外部请求，不做缓存。
 * 单个 Provider 或单个市场源失败只记录告警，不影响其余来源。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPluginCatalogServiceImpl implements AiPluginCatalogService {

    /**
     * 插件配置分组
     */
    private static final String PLUGINS_GROUP = "plugins";

    /**
     * Provider 开关键前后缀
     */
    private static final String PROVIDER_KEY_PREFIX = "provider_";
    private static final String PROVIDER_KEY_SUFFIX = "_enabled";

    /**
     * 本地插件扫描缓存有效期
     */
    private static final long LOCAL_CACHE_TTL_MILLIS = 60_000L;

    /**
     * 单插件开关未配置时的默认值：本地已安装插件默认可用
     */
    private static final boolean DEFAULT_PLUGIN_ENABLED = true;

    private final SysAiGlobalSettingService globalSettingService;
    private final List<PluginOfflineProvider> offlineProviders;

    /**
     * 本地插件快照：Provider 键 → 插件条目列表，volatile 保证可见性
     */
    private volatile Map<String, List<Map<String, Object>>> localSnapshot;

    /**
     * 快照生成时间戳（毫秒）
     */
    private volatile long localSnapshotAt;

    @Override
    public boolean isProviderEnabled(String providerKey, boolean defaultValue) {
        if (providerKey == null || providerKey.isBlank()) {
            return defaultValue;
        }
        return globalSettingService.getBoolean(PLUGINS_GROUP, providerField(providerKey), defaultValue);
    }

    @Override
    public void setProviderEnabled(String providerKey, boolean enabled) {
        if (providerKey == null || providerKey.isBlank()) {
            return;
        }
        try {
            globalSettingService.upsertValue(PLUGINS_GROUP, providerField(providerKey), String.valueOf(enabled),
                    "Plugins Provider " + providerKey + " enabled");
            // 扫描结果按 Provider 分组缓存，开关变更后必须作废，否则列表在缓存有效期内仍是旧来源
            refresh();
        } catch (Exception e) {
            log.warn("[PluginCatalog] 保存 Provider 开关失败 key={}: {}", providerKey, e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> listLocalPlugins() {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (List<Map<String, Object>> items : localSnapshotMap().values()) {
            for (Map<String, Object> item : items) {
                mergeById(byId, item);
            }
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * 按插件标识合并条目。
     *
     * <p>同一个扩展常被多个编辑器来源重复上报（例如 VS Code 目录会被 cline、copilot、
     * roo-code 各识别一次），此处只保留一份记录，并把全部来源收集到 {@code sources} 字段。</p>
     *
     * @param byId  已合并条目，键为插件标识
     * @param item  缓存中的插件条目，不允许为 null
     */
    private void mergeById(Map<String, Map<String, Object>> byId, Map<String, Object> item) {
        Object id = item.get("id");
        String key = id != null && !String.valueOf(id).isBlank()
                ? String.valueOf(id)
                : item.get("source") + "/" + item.get("name");
        Map<String, Object> existing = byId.get(key);
        if (existing == null) {
            Map<String, Object> merged = withPluginSwitch(item);
            List<Object> sources = new ArrayList<>();
            if (item.get("source") != null) {
                sources.add(item.get("source"));
            }
            merged.put("sources", sources);
            byId.put(key, merged);
            return;
        }
        List<Object> sources = sourcesOf(existing);
        Object source = item.get("source");
        if (source != null && !sources.contains(source)) {
            sources.add(source);
        }
    }

    /**
     * 取条目的来源集合。
     *
     * @param item 已合并的插件条目
     * @return 来源列表，字段缺失时补建空列表
     */
    @SuppressWarnings("unchecked")
    private List<Object> sourcesOf(Map<String, Object> item) {
        Object sources = item.get("sources");
        if (sources instanceof List) {
            return (List<Object>) sources;
        }
        List<Object> created = new ArrayList<>();
        item.put("sources", created);
        return created;
    }

    @Override
    public int countLocalPlugins(String providerKey) {
        List<Map<String, Object>> items = localSnapshotMap().get(providerKey);
        if (items == null) {
            return 0;
        }
        // 同一扩展会被多个编辑器目录重复上报，计数口径与列表一致，按插件标识去重
        Map<String, Boolean> ids = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            Object id = item.get("id");
            ids.put(id != null && !String.valueOf(id).isBlank() ? String.valueOf(id)
                    : item.get("source") + "/" + item.get("name"), Boolean.TRUE);
        }
        return ids.size();
    }

    @Override
    public Map<String, Object> findLocalPlugin(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            return null;
        }
        for (List<Map<String, Object>> items : localSnapshotMap().values()) {
            for (Map<String, Object> item : items) {
                if (pluginId.equals(item.get("id")) || pluginId.equals(item.get("pluginId"))) {
                    return withPluginSwitch(item);
                }
            }
        }
        return null;
    }

    @Override
    public void refresh() {
        localSnapshot = null;
        localSnapshotAt = 0L;
    }

    @Override
    public List<Map<String, Object>> searchMarketPlugins(String keyword) {
        List<Map<String, Object>> plugins = new ArrayList<>();
        Map<String, PluginMarketSourceProvider> marketProviders = marketProviders();
        if (marketProviders.isEmpty()) {
            return plugins;
        }
        var onlineIndex = onlineSourceIndex();
        for (Map.Entry<String, PluginMarketSourceProvider> entry : marketProviders.entrySet()) {
            PluginMarketSourceProvider market = entry.getValue();
            String providerKey = "online_" + market.source();
            if (!isProviderEnabled(providerKey, market.defaultEnabled())) {
                continue;
            }
            var source = onlineIndex.get(market.source().toLowerCase());
            if (source == null) {
                log.info("[PluginCatalog] 市场源 {} 无对应的在线插件检索实现，跳过", market.source());
                continue;
            }
            plugins.addAll(searchSource(source, market, keyword));
        }
        return plugins;
    }

    /**
     * 建立在线插件来源索引。
     *
     * <p>SPI 注册表返回的键会被大写（如 {@code OPEN-VSX}），因此只能以来源自身
     * {@code name()} 的小写形式作为索引键，才能与市场源的 {@code source()} 对上。</p>
     *
     * @return 小写来源名 → 在线插件来源
     */
    private Map<String, com.chua.common.support.datasearch.plugin.spi.PluginOnlineProvider> onlineSourceIndex() {
        Map<String, com.chua.common.support.datasearch.plugin.spi.PluginOnlineProvider> index = new LinkedHashMap<>();
        for (var entry : onlineSources().entrySet()) {
            var source = entry.getValue();
            if (source == null) {
                continue;
            }
            try {
                index.putIfAbsent(source.name().toLowerCase(), source);
            } catch (Exception e) {
                log.warn("[PluginCatalog] 在线插件来源 {} 取名失败: {}", entry.getKey(), e.getMessage());
            }
        }
        return index;
    }

    /**
     * 在单个在线市场源中检索插件。
     *
     * @param source  底层在线插件来源，不允许为 null
     * @param market  对应的市场源配置，用于标注条目归属
     * @param keyword 搜索关键词，为空时取默认展示列表
     * @return 带单插件开关的条目列表
     */
    private List<Map<String, Object>> searchSource(
            com.chua.common.support.datasearch.plugin.spi.PluginOnlineProvider source,
            PluginMarketSourceProvider market, String keyword) {
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            List<com.chua.common.support.datasearch.plugin.model.PluginDefinition> definitions =
                    keyword == null || keyword.isBlank() ? source.getPlugins() : source.search(keyword);
            if (definitions == null) {
                return items;
            }
            for (var definition : definitions) {
                if (definition == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", definition.getId());
                item.put("pluginId", definition.getId());
                item.put("name", definition.getName());
                item.put("description", definition.getDescription());
                item.put("author", definition.getAuthor());
                item.put("version", definition.getVersion());
                item.put("location", definition.getLocation());
                item.put("source", market.source());
                item.put("origin", "market");
                item.put("providerKey", "online_" + market.source());
                items.add(withPluginSwitch(item));
            }
        } catch (Exception e) {
            log.warn("[PluginCatalog] 市场源 {} 检索失败: {}", market.source(), e.getMessage());
        }
        return items;
    }

    /**
     * 取本地插件快照，过期则重建。
     *
     * @return Provider 键 → 插件条目列表（缓存实例，调用方不得修改）
     */
    private Map<String, List<Map<String, Object>>> localSnapshotMap() {
        Map<String, List<Map<String, Object>>> snapshot = localSnapshot;
        if (snapshot != null && System.currentTimeMillis() - localSnapshotAt < LOCAL_CACHE_TTL_MILLIS) {
            return snapshot;
        }
        synchronized (this) {
            snapshot = localSnapshot;
            if (snapshot != null && System.currentTimeMillis() - localSnapshotAt < LOCAL_CACHE_TTL_MILLIS) {
                return snapshot;
            }
            snapshot = scanLocalPlugins();
            localSnapshot = snapshot;
            localSnapshotAt = System.currentTimeMillis();
            return snapshot;
        }
    }

    /**
     * 扫描全部启用的本地 Provider。
     *
     * @return Provider 键 → 插件条目列表
     */
    private Map<String, List<Map<String, Object>>> scanLocalPlugins() {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        if (offlineProviders == null) {
            return grouped;
        }
        for (PluginOfflineProvider provider : offlineProviders) {
            if (provider == null) {
                continue;
            }
            String providerKey = "local_" + provider.getClass().getSimpleName();
            if (!isProviderEnabled(providerKey, true)) {
                continue;
            }
            List<Map<String, Object>> items = new ArrayList<>();
            try {
                List<Map<String, Object>> scanned = provider.listPlugins();
                if (scanned != null) {
                    for (Map<String, Object> item : scanned) {
                        if (item == null) {
                            continue;
                        }
                        Map<String, Object> copy = new LinkedHashMap<>(item);
                        copy.put("providerKey", providerKey);
                        copy.put("providerName", safeName(provider));
                        items.add(copy);
                    }
                }
            } catch (Exception e) {
                log.warn("[PluginCatalog] 本地来源 {} 扫描失败: {}", providerKey, e.getMessage());
            }
            grouped.put(providerKey, items);
        }
        return grouped;
    }

    /**
     * 复制条目并附加单插件开关状态。
     *
     * @param item 缓存中的插件条目，不允许为 null
     * @return 供接口返回的新条目
     */
    private Map<String, Object> withPluginSwitch(Map<String, Object> item) {
        Map<String, Object> copy = new LinkedHashMap<>(item);
        Object pluginId = item.get("id");
        boolean enabled = pluginId == null ? DEFAULT_PLUGIN_ENABLED
                : isPluginEnabled(String.valueOf(pluginId));
        copy.put("enabled", enabled);
        return copy;
    }

    /**
     * 读取单个插件的开关状态。
     *
     * @param pluginId 插件标识，不允许为 null
     * @return 该插件是否启用
     */
    private boolean isPluginEnabled(String pluginId) {
        try {
            return globalSettingService.getBoolean(PLUGINS_GROUP, pluginId, DEFAULT_PLUGIN_ENABLED);
        } catch (Exception e) {
            log.warn("[PluginCatalog] 读取插件开关失败 id={}: {}", pluginId, e.getMessage());
            return DEFAULT_PLUGIN_ENABLED;
        }
    }

    /**
     * 构造 Provider 开关的设置字段名。
     *
     * @param providerKey Provider 键
     * @return 设置字段名
     */
    private String providerField(String providerKey) {
        return PROVIDER_KEY_PREFIX + providerKey + PROVIDER_KEY_SUFFIX;
    }

    /**
     * 取 Provider 展示名，异常时退回类名。
     *
     * @param provider 本地插件来源
     * @return 展示名
     */
    private String safeName(PluginOfflineProvider provider) {
        try {
            return provider.name();
        } catch (Exception e) {
            return provider.getClass().getSimpleName();
        }
    }

    /**
     * 加载在线市场源 SPI 实现。
     *
     * @return 源键 → 市场源实现，无实现时返回空映射
     */
    private Map<String, PluginMarketSourceProvider> marketProviders() {
        try {
            Map<String, PluginMarketSourceProvider> providers = ServiceProvider.of(PluginMarketSourceProvider.class).list();
            return providers != null ? providers : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("[PluginCatalog] 加载插件市场源失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 加载 datasearch 的在线插件来源 SPI 实现。
     *
     * @return SPI 键 → 在线来源，无实现时返回空映射
     */
    private Map<String, com.chua.common.support.datasearch.plugin.spi.PluginOnlineProvider> onlineSources() {
        try {
            Map<String, com.chua.common.support.datasearch.plugin.spi.PluginOnlineProvider> sources =
                    ServiceProvider.of(com.chua.common.support.datasearch.plugin.spi.PluginOnlineProvider.class).list();
            return sources != null ? sources : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("[PluginCatalog] 加载在线插件来源失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
