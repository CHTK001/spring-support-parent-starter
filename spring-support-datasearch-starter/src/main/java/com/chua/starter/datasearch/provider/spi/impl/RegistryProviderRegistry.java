package com.chua.starter.datasearch.provider.spi.impl;

import com.chua.starter.datasearch.provider.model.ProviderCapability;
import com.chua.starter.datasearch.provider.model.ProviderDescriptor;
import com.chua.starter.datasearch.provider.spi.ProviderRegistryProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 登记表实现：读 {@code provider-catalog/provider-registry.json}。
 *
 * <p>数据由前端模板库（provider id / 名称 / 说明 / 协议 / 默认地址）与 models.dev
 * 目录（官方文档、目录键、别名、主机名）生成，覆盖模板库全部供应商。文件顺序即
 * 优先级：同一个 models.dev 目录键被多家共用时（如 alibaba 对应 qwen / qianfan），
 * 先出现的登记项胜出，其余仍可按自己的 id 命中。</p>
 *
 * <p>匹配顺序：先精确命中 {@code aliases}，再按主机名（相等或子域）匹配。
 * 文件缺失或解析失败时返回空登记表，不影响调用方主流程。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@Slf4j
public class RegistryProviderRegistry implements ProviderRegistryProvider {

    /**
     * 登记表资源路径
     */
    private static final String RESOURCE = "/provider-catalog/provider-registry.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 已解析的登记表
     */
    private static volatile Snapshot snapshot;

    @Override
    public String name() {
        return "registry";
    }

    @Override
    public boolean supports(String platform, String baseUrl) {
        return find(platform, baseUrl) != null;
    }

    @Override
    public ProviderDescriptor find(String platform, String baseUrl) {
        Snapshot current = snapshot();
        String id = normalize(platform);
        if (!id.isEmpty()) {
            ProviderDescriptor hit = current.byAlias.get(id);
            if (hit != null) {
                return hit;
            }
        }
        String host = hostOf(baseUrl);
        if (host.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, ProviderDescriptor> entry : current.byHost.entrySet()) {
            String candidate = entry.getKey();
            if (host.equals(candidate) || host.endsWith("." + candidate)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public List<ProviderDescriptor> entries() {
        return snapshot().entries;
    }

    @Override
    public int size() {
        return snapshot().entries.size();
    }

    /**
     * 登记表（懒加载一次）。
     *
     * @return 登记表；加载失败返回空登记表
     */
    private static Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current != null) {
            return current;
        }
        return load();
    }

    /**
     * 读取并索引登记表。
     *
     * @return 登记表
     */
    private static synchronized Snapshot load() {
        if (snapshot != null) {
            return snapshot;
        }
        Snapshot loaded = new Snapshot();
        try (InputStream in = RegistryProviderRegistry.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("[provider] 供应商登记表缺失: {}", RESOURCE);
            } else {
                JsonNode root = MAPPER.readTree(in);
                for (JsonNode node : root.path("providers")) {
                    ProviderDescriptor entry = parse(node);
                    if (entry == null) {
                        continue;
                    }
                    loaded.entries.add(entry);
                    // 第一遍只登记本家 id：别名与 models.dev 目录键后登记，
                    // 保证「某家的别名恰好是另一家的 id」时不会把后者整个顶掉
                    loaded.byAlias.putIfAbsent(normalize(entry.getId()), entry);
                    for (String host : entry.getHosts() == null ? List.<String>of() : entry.getHosts()) {
                        if (!host.isEmpty()) {
                            loaded.byHost.putIfAbsent(host, entry);
                        }
                    }
                }
                for (ProviderDescriptor entry : loaded.entries) {
                    for (String alias : entry.getAliases() == null ? List.<String>of() : entry.getAliases()) {
                        loaded.byAlias.putIfAbsent(alias, entry);
                    }
                    String modelsDevId = normalize(entry.getModelsDevId());
                    if (!modelsDevId.isEmpty()) {
                        loaded.byAlias.putIfAbsent(modelsDevId, entry);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[provider] 供应商登记表解析失败: {}", e.toString());
            loaded.entries.clear();
        }
        loaded.entries = Collections.unmodifiableList(loaded.entries);
        snapshot = loaded;
        log.info("[provider] 供应商登记表 {} 家，目录键 {} 家，官方余额 {} 家",
                loaded.entries.size(),
                loaded.entries.stream().filter(e -> e.getModelsDevId() != null).count(),
                loaded.entries.stream().filter(e -> "official".equals(e.getCreditMode())).count());
        return loaded;
    }

    /**
     * 解析单条登记项。
     *
     * @param node JSON 节点
     * @return 登记项；缺 id 返回 null
     */
    private static ProviderDescriptor parse(JsonNode node) {
        String id = node.path("id").asText("").trim();
        if (id.isEmpty()) {
            return null;
        }
        ProviderDescriptor entry = new ProviderDescriptor();
        entry.setId(id);
        entry.setName(text(node, "name", id));
        entry.setDescription(text(node, "description", null));
        entry.setProtocol(text(node, "protocol", "openai"));
        entry.setBaseUrl(text(node, "baseUrl", null));
        entry.setDoc(text(node, "doc", null));
        entry.setModelsDevId(text(node, "modelsDevId", null));
        entry.setAliases(strings(node.path("aliases")));
        entry.setHosts(strings(node.path("hosts")));
        entry.setCapability(capability(node.path("capabilities")));
        JsonNode credit = node.path("credit");
        entry.setCreditMode(text(credit, "mode", null));
        entry.setCreditKind(text(credit, "kind", null));
        entry.setCreditProviderId(text(credit, "providerId", null));
        entry.setCreditCurrency(text(credit, "currency", null));
        entry.setLimitsVerified(node.path("limits").path("verified").asBoolean(false));
        return entry;
    }

    /**
     * 解析能力位。
     *
     * @param node 能力节点
     * @return 能力位；节点缺失时返回 null
     */
    private static ProviderCapability capability(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        ProviderCapability capability = new ProviderCapability();
        capability.setStreaming(bool(node, "streaming"));
        capability.setStreamingSource(text(node, "streamingSource", null));
        capability.setAsyncSupported(bool(node, "async"));
        capability.setAsyncSource(text(node, "asyncSource", null));
        capability.setSource(text(node, "source", null));
        return capability;
    }

    /**
     * 读文本字段。
     *
     * @param node         节点
     * @param key          键
     * @param defaultValue 缺省值
     * @return 文本；缺失返回缺省值
     */
    private static String text(JsonNode node, String key, String defaultValue) {
        JsonNode value = node.path(key);
        return value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : defaultValue;
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
     * 读字符串数组（小写去空去重）。
     *
     * @param node 数组节点
     * @return 值
     */
    private static List<String> strings(JsonNode node) {
        List<String> list = new ArrayList<>(4);
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String value = normalize(item.asText(""));
                if (!value.isEmpty() && !list.contains(value)) {
                    list.add(value);
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
     * 登记表索引。
     */
    private static final class Snapshot {

        /**
         * 登记项（文件顺序）
         */
        private List<ProviderDescriptor> entries = new ArrayList<>(256);

        /**
         * 别名 → 登记项
         */
        private final Map<String, ProviderDescriptor> byAlias = new LinkedHashMap<>(512);

        /**
         * 主机名 → 登记项
         */
        private final Map<String, ProviderDescriptor> byHost = new LinkedHashMap<>(512);
    }
}
