package com.chua.starter.datasearch.provider.spi.impl;

import com.chua.starter.datasearch.provider.model.ProviderModelCatalog;
import com.chua.starter.datasearch.provider.model.ProviderModelInfo;
import com.chua.starter.datasearch.provider.spi.ProviderModelCatalogProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * models.dev 目录实现：读随包发布的快照 {@code provider-catalog/models-dev.json.gz}。
 *
 * <p>快照由 {@code pages/ai/docs/gen-providers-doc.js} 同源脚本生成：厂商、别名、
 * 官方文档入口取自模板库与 models.dev，模型说明 / 价格 / 上下文 / 模态 / 能力位取自
 * models.dev 的模型字段。{@code streaming} / {@code asyncSupported} 目录里没有，
 * 一律留空。</p>
 *
 * <p>匹配顺序：先精确命中 {@code aliases}，再按主机名（相等或子域）匹配。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Slf4j
public class ModelsDevCatalogProvider implements ProviderModelCatalogProvider {

    /**
     * 快照资源路径
     */
    private static final String RESOURCE = "/provider-catalog/models-dev.json.gz";

    /**
     * 数据源标识
     */
    private static final String SOURCE = "models.dev";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 已解析的快照
     */
    private static volatile Snapshot snapshot;

    @Override
    public String name() {
        return "modelsdev";
    }

    @Override
    public boolean supports(String platform, String baseUrl) {
        return findProvider(platform, baseUrl) != null;
    }

    @Override
    public ProviderModelCatalog query(String platform, String baseUrl) {
        JsonNode provider = findProvider(platform, baseUrl);
        if (provider == null) {
            return null;
        }
        ProviderModelCatalog catalog = new ProviderModelCatalog();
        catalog.setProviderId(provider.path("id").asText(""));
        catalog.setName(provider.path("name").asText(catalog.getProviderId()));
        catalog.setDoc(provider.path("doc").isTextual() ? provider.path("doc").asText() : null);
        catalog.setSource(SOURCE);
        List<ProviderModelInfo> models = new ArrayList<>(64);
        JsonNode modelNodes = provider.path("models");
        if (modelNodes.isObject()) {
            modelNodes.fields().forEachRemaining(entry -> {
                ProviderModelInfo model = parseModel(entry.getKey(), entry.getValue());
                if (model != null) {
                    models.add(model);
                }
            });
        }
        catalog.setModels(models);
        return catalog;
    }

    @Override
    public int providerCount() {
        return snapshot().providers.size();
    }

    @Override
    public int modelCount() {
        return snapshot().modelCount;
    }

    /**
     * 按平台 / 地址找厂商节点。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 厂商节点；未收录返回 null
     */
    private JsonNode findProvider(String platform, String baseUrl) {
        Snapshot current = snapshot();
        String id = normalize(platform);
        if (!id.isEmpty()) {
            JsonNode hit = current.byAlias.get(id);
            if (hit != null) {
                return hit;
            }
        }
        String host = hostOf(baseUrl);
        if (host.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, JsonNode> entry : current.byHost.entrySet()) {
            String candidate = entry.getKey();
            if (host.equals(candidate) || host.endsWith("." + candidate)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 解析单个模型节点。
     *
     * @param id   模型键
     * @param node 模型节点
     * @return 模型信息；缺 id 返回 null
     */
    private static ProviderModelInfo parseModel(String id, JsonNode node) {
        if (id == null || id.isBlank()) {
            return null;
        }
        ProviderModelInfo model = new ProviderModelInfo();
        model.setId(node.path("id").asText(id));
        model.setName(node.path("name").asText(model.getId()));
        model.setDescription(text(node, "description"));
        JsonNode cost = node.path("cost");
        model.setPriceInputPerMusd(decimal(cost, "input"));
        model.setPriceOutputPerMusd(decimal(cost, "output"));
        model.setPriceCacheHitPerMusd(decimal(cost, "cache_read"));
        model.setPriceCacheWritePerMusd(decimal(cost, "cache_write"));
        JsonNode limit = node.path("limit");
        model.setContextWindow(integer(limit, "context"));
        model.setMaxOutputTokens(integer(limit, "output"));
        JsonNode modalities = node.path("modalities");
        model.setInputModalities(strings(modalities.path("input")));
        model.setOutputModalities(strings(modalities.path("output")));
        model.setAttachment(bool(node, "attachment"));
        model.setReasoning(bool(node, "reasoning"));
        model.setToolCall(bool(node, "tool_call"));
        model.setStructuredOutput(bool(node, "structured_output"));
        model.setTemperature(bool(node, "temperature"));
        model.setOpenWeights(bool(node, "open_weights"));
        model.setKnowledge(text(node, "knowledge"));
        model.setReleaseDate(text(node, "release_date"));
        model.setLastUpdated(text(node, "last_updated"));
        // 目录未提供流式 / 异步标记：保持 null，不按协议默认成 true
        return model;
    }

    /**
     * 快照（懒加载一次）。
     *
     * @return 快照；加载失败返回空快照
     */
    private static Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current != null) {
            return current;
        }
        return load();
    }

    /**
     * 读取并索引快照。
     *
     * @return 快照
     */
    private static synchronized Snapshot load() {
        if (snapshot != null) {
            return snapshot;
        }
        Snapshot loaded = new Snapshot();
        try (InputStream raw = ModelsDevCatalogProvider.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                log.warn("[provider] 模型目录快照缺失: {}", RESOURCE);
                snapshot = loaded;
                return loaded;
            }
            try (InputStream in = new GZIPInputStream(raw)) {
                JsonNode root = MAPPER.readTree(in);
                JsonNode providers = root.path("providers");
                providers.fields().forEachRemaining(entry -> {
                    JsonNode node = entry.getValue();
                    loaded.providers.put(entry.getKey(), node);
                    loaded.modelCount += node.path("models").size();
                    for (JsonNode alias : node.path("aliases")) {
                        loaded.byAlias.putIfAbsent(normalize(alias.asText("")), node);
                    }
                    loaded.byAlias.putIfAbsent(normalize(entry.getKey()), node);
                    for (JsonNode host : node.path("hosts")) {
                        String value = normalize(host.asText(""));
                        if (!value.isEmpty()) {
                            loaded.byHost.putIfAbsent(value, node);
                        }
                    }
                });
            }
        } catch (Exception e) {
            log.warn("[provider] 模型目录快照解析失败: {}", e.toString());
            snapshot = new Snapshot();
            return snapshot;
        }
        snapshot = loaded;
        log.info("[provider] 模型目录快照：{} 家厂商、{} 个模型", loaded.providers.size(), loaded.modelCount);
        return loaded;
    }

    /**
     * 读文本字段。
     *
     * @param node 节点
     * @param key  键
     * @return 文本；缺失返回 null
     */
    private static String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    /**
     * 读布尔字段。
     *
     * @param node 节点
     * @param key  键
     * @return 布尔；缺失返回 null
     */
    private static Boolean bool(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isBoolean() ? value.asBoolean() : null;
    }

    /**
     * 读小数字段。
     *
     * @param node 节点
     * @param key  键
     * @return 小数；缺失返回 null
     */
    private static Double decimal(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isNumber() ? value.asDouble() : null;
    }

    /**
     * 读长整数字段。
     *
     * @param node 节点
     * @param key  键
     * @return 长整数；缺失返回 null
     */
    private static Long integer(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isNumber() ? value.asLong() : null;
    }

    /**
     * 读字符串数组。
     *
     * @param node 数组节点
     * @return 值
     */
    private static List<String> strings(JsonNode node) {
        List<String> list = new ArrayList<>(4);
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    list.add(item.asText());
                }
            }
        }
        return list;
    }

    /**
     * 归一化 provider id / 主机名。
     *
     * @param value 原值
     * @return 小写去空白
     */
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 提取 URL 主机名。
     *
     * @param url 地址
     * @return 主机名；失败返回空串
     */
    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 快照索引。
     */
    private static final class Snapshot {

        /**
         * 厂商键 → 节点
         */
        private final Map<String, JsonNode> providers = new LinkedHashMap<>(256);

        /**
         * 别名 → 节点
         */
        private final Map<String, JsonNode> byAlias = new LinkedHashMap<>(512);

        /**
         * 主机名 → 节点
         */
        private final Map<String, JsonNode> byHost = new LinkedHashMap<>(512);

        /**
         * 模型总数
         */
        private int modelCount;
    }
}
