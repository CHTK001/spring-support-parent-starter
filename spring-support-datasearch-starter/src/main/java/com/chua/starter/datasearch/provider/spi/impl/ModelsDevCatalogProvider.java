package com.chua.starter.datasearch.provider.spi.impl;

import com.chua.starter.datasearch.provider.model.ProviderCapability;
import com.chua.starter.datasearch.provider.model.ProviderDescriptor;
import com.chua.starter.datasearch.provider.model.ProviderModelCatalog;
import com.chua.starter.datasearch.provider.model.ProviderModelInfo;
import com.chua.starter.datasearch.provider.spi.ProviderModelCatalogProvider;
import com.chua.starter.datasearch.provider.support.ProviderCapabilities;
import com.chua.starter.datasearch.provider.support.ProviderPricings;
import com.chua.starter.datasearch.provider.support.ProviderRegistries;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
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
 * 由能力位 SPI（{@code ProviderCapabilityProvider}）补齐，判定口径仍是
 * {@link com.chua.starter.datasearch.provider.support.ProviderProtocolCapabilities}
 * 的「流式走协议、异步只认实测端点」；仍无口径时保持 {@code null} 表示「未收录」。</p>
 *
 * <p>匹配顺序：先按供应商登记表的 {@code modelsDevId} 取（解决 {@code together →
 * togetherai}、{@code qwen → alibaba} 这类别名），再精确命中 {@code aliases}，
 * 最后按主机名（相等或子域）匹配。</p>
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
        catalog.setPricingRules(ProviderPricings.of(platform, baseUrl));
        // 目录没有流式 / 异步字段：由能力位 SPI 补齐（流式走协议、异步只认实测端点）
        ProviderCapability capability = ProviderCapabilities.of(platform, baseUrl);
        if (capability != null) {
            for (ProviderModelInfo model : models) {
                if (capability.getStreaming() != null) {
                    model.setStreaming(capability.getStreaming());
                    model.setStreamingSource(capability.getStreamingSource());
                }
                if (capability.getAsyncSupported() != null) {
                    model.setAsyncSupported(capability.getAsyncSupported());
                    model.setAsyncSource(capability.getAsyncSource());
                }
            }
        }
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
        // 登记表给出 models.dev 目录键：别名与前端 id 不一致时（together → togetherai、
        // qwen → alibaba、aws-bedrock → amazon-bedrock）先按目录键取，避免只靠字符串碰运气
        ProviderDescriptor descriptor = ProviderRegistries.find(platform, baseUrl);
        String modelsDevId = descriptor == null ? null : descriptor.getModelsDevId();
        if (modelsDevId != null && !modelsDevId.isBlank()) {
            JsonNode hit = current.providers.get(modelsDevId);
            if (hit != null) {
                return hit;
            }
        }
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
     * 按模型 id 在全部厂商里查同名模型。
     *
     * <p>同一模型常被多家厂商 / 网关同时收录（{@code deepseek-v4-pro} 在 deepseek、
     * opencode、sensenova 下都有），所以这里返回<b>候选列表</b>，由调用方按自己的渠道
     * 挑一个；精确 id 未命中时按折叠键回退（忽略大小写、分隔符与厂商前缀），与定价侧
     * {@code DataSearchModelPricingProvider} 的口径一致。</p>
     *
     * @param modelId 模型 id（可带厂商前缀）
     * @return 候选（厂商 id + 模型信息）；未收录返回空列表
     */
    static List<ModelEntry> findByModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return List.of();
        }
        Snapshot current = snapshot();
        List<ModelNode> nodes = current.byModelId.get(modelId);
        if (nodes == null) {
            nodes = current.byFoldedModelId.get(foldModelId(modelId));
        }
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<ModelEntry> out = new ArrayList<>(nodes.size());
        for (ModelNode node : nodes) {
            ProviderModelInfo model = parseModel(node.modelId(), node.node());
            if (model != null) {
                out.add(new ModelEntry(node.providerId(), model));
            }
        }
        return out;
    }

    /**
     * 判断目录里是否收录了该模型（不做解析，只查索引）。
     *
     * @param modelId 模型 id
     * @return 收录返回 true
     */
    static boolean hasModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        Snapshot current = snapshot();
        return current.byModelId.containsKey(modelId)
                || current.byFoldedModelId.containsKey(foldModelId(modelId));
    }

    /**
     * 目录收录的模型总数（跨厂商去重前的条目数）。
     *
     * @return 模型数
     */
    static int totalModels() {
        return snapshot().modelCount;
    }

    /**
     * 目录收录的全部模型 id（跨厂商去重，与 {@link #findByModelId(String)} 同一索引）。
     *
     * @return 模型 id 集合（只读视图）；快照未加载时为空集合
     */
    static java.util.Set<String> modelIds() {
        return java.util.Collections.unmodifiableSet(snapshot().byModelId.keySet());
    }

    /**
     * 折叠模型 id：剥掉厂商前缀与所有分隔符，只留小写字母数字。
     *
     * @param modelId 模型 id
     * @return 折叠值；只剩分隔符时返回空串
     */
    private static String foldModelId(String modelId) {
        String name = modelId;
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < name.length()) {
            name = name.substring(slash + 1);
        }
        StringBuilder builder = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                builder.append(ch);
            } else if (ch >= 'A' && ch <= 'Z') {
                builder.append((char) (ch + ('a' - 'A')));
            }
        }
        return builder.toString();
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
        // 目录没有流式 / 异步字段：先留空，由 query() 用能力位统一补齐
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
                providers.fields().forEachRemaining(providerEntry -> {
                    String providerId = providerEntry.getKey();
                    JsonNode node = providerEntry.getValue();
                    loaded.providers.put(providerId, node);
                    JsonNode models = node.path("models");
                    loaded.modelCount += models.size();
                    // 模型 id → 厂商节点：同一模型被多家收录时全部保留，供「按模型查指标」挑渠道
                    models.fields().forEachRemaining(modelEntry -> {
                        String modelId = modelEntry.getKey();
                        ModelNode hit = new ModelNode(providerId, modelId, modelEntry.getValue());
                        loaded.byModelId.computeIfAbsent(modelId, key -> new ArrayList<>(4)).add(hit);
                        String folded = foldModelId(modelId);
                        if (!folded.isEmpty()) {
                            loaded.byFoldedModelId.computeIfAbsent(folded, key -> new ArrayList<>(4)).add(hit);
                        }
                    });
                    for (JsonNode alias : node.path("aliases")) {
                        loaded.byAlias.putIfAbsent(normalize(alias.asText("")), node);
                    }
                    loaded.byAlias.putIfAbsent(normalize(providerId), node);
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
         * 模型 id → 收录该模型的厂商节点
         */
        private final Map<String, List<ModelNode>> byModelId = new HashMap<>(8192);

        /**
         * 折叠后的模型 id → 收录该模型的厂商节点
         */
        private final Map<String, List<ModelNode>> byFoldedModelId = new HashMap<>(8192);

        /**
         * 模型总数
         */
        private int modelCount;
    }

    /**
     * 索引里的一个「厂商 → 模型」条目（只存节点，解析留给查询时，避免共享可变对象）。
     *
     * @param providerId 厂商标识
     * @param modelId    模型键（models 对象里的一级键）
     * @param node       模型节点
     */
    record ModelNode(String providerId, String modelId, JsonNode node) {
    }

    /**
     * 按模型查指标的结果条目。
     *
     * @param providerId 收录该模型的厂商标识
     * @param model      模型信息（每次查询新解析，调用方可安全修改）
     */
    record ModelEntry(String providerId, ProviderModelInfo model) {
    }
}
