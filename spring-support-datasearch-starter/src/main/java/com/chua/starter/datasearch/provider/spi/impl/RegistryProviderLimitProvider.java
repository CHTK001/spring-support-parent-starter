package com.chua.starter.datasearch.provider.spi.impl;

import com.chua.starter.datasearch.provider.model.ProviderLimitInfo;
import com.chua.starter.datasearch.provider.spi.ProviderLimitProvider;
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
 * 登记表实现：读 {@code provider-catalog/provider-limit-docs.json}。
 *
 * <p>数据由模板库（provider id / 默认地址）与 models.dev 目录（厂商 {@code doc} 字段）
 * 生成，覆盖 200+ 家；只有核对过官方原文的条目才带数值 / 计费口径。</p>
 *
 * <p>匹配顺序：先精确命中 {@code aliases}（一家一个键，不会串），再按主机名
 * （相等或子域）匹配；文件顺序即优先级。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Slf4j
public class RegistryProviderLimitProvider implements ProviderLimitProvider {

    /**
     * 登记表资源路径
     */
    private static final String RESOURCE = "/provider-catalog/provider-limit-docs.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 懒加载的登记项
     */
    private static volatile List<ProviderLimitInfo> cachedEntries;

    @Override
    public String name() {
        return "registry";
    }

    @Override
    public boolean supports(String platform, String baseUrl) {
        return query(platform, baseUrl) != null;
    }

    @Override
    public ProviderLimitInfo query(String platform, String baseUrl) {
        String id = normalize(platform);
        String host = hostOf(baseUrl);
        for (ProviderLimitInfo entry : loadEntries()) {
            if (!id.isEmpty() && entry.getAliases() != null && entry.getAliases().contains(id)) {
                return entry;
            }
        }
        if (host.isEmpty()) {
            return null;
        }
        for (ProviderLimitInfo entry : loadEntries()) {
            if (entry.getHosts() == null) {
                continue;
            }
            for (String candidate : entry.getHosts()) {
                if (host.equals(candidate) || host.endsWith("." + candidate)) {
                    return entry;
                }
            }
        }
        return null;
    }

    @Override
    public List<ProviderLimitInfo> entries() {
        return loadEntries();
    }

    @Override
    public int size() {
        return loadEntries().size();
    }

    /**
     * 读取登记表（懒加载一次）。
     *
     * @return 登记项
     */
    private static List<ProviderLimitInfo> loadEntries() {
        List<ProviderLimitInfo> current = cachedEntries;
        if (current != null) {
            return current;
        }
        return load();
    }

    /**
     * 读取登记表；失败时返回空列表，不影响调用方主流程。
     *
     * @return 登记项
     */
    private static synchronized List<ProviderLimitInfo> load() {
        if (cachedEntries != null) {
            return cachedEntries;
        }
        List<ProviderLimitInfo> loaded = new ArrayList<>(256);
        try (InputStream in = RegistryProviderLimitProvider.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("[provider] 限制登记表缺失: {}", RESOURCE);
            } else {
                JsonNode root = MAPPER.readTree(in);
                for (JsonNode node : root.path("entries")) {
                    ProviderLimitInfo info = parse(node);
                    if (info != null) {
                        loaded.add(info);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[provider] 限制登记表解析失败: {}", e.toString());
            loaded.clear();
        }
        cachedEntries = Collections.unmodifiableList(loaded);
        log.info("[provider] 限制登记表 {} 条，官方文档入口 {} 条",
                cachedEntries.size(), cachedEntries.stream().filter(e -> e.getDoc() != null).count());
        return cachedEntries;
    }

    /**
     * 解析单条登记项。
     *
     * @param node JSON 节点
     * @return 登记项；缺 id 返回 null
     */
    @SuppressWarnings("unchecked")
    private static ProviderLimitInfo parse(JsonNode node) {
        String id = node.path("id").asText("").trim();
        if (id.isEmpty()) {
            return null;
        }
        ProviderLimitInfo info = new ProviderLimitInfo();
        info.setId(id);
        info.setName(node.path("name").asText(id));
        info.setDoc(node.path("doc").isTextual() && !node.path("doc").asText().isBlank()
                ? node.path("doc").asText().trim() : null);
        info.setVerified(node.path("verified").asBoolean(false));
        info.setNotes(node.path("notes").isTextual() ? node.path("notes").asText() : null);
        info.setAliases(strings(node.path("aliases")));
        info.setHosts(strings(node.path("hosts")));
        Map<String, Object> limits = MAPPER.convertValue(node.path("limits"), Map.class);
        info.setLimits(limits == null ? new LinkedHashMap<>() : limits);
        return info;
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
                String text = normalize(item.asText(""));
                if (!text.isEmpty() && !list.contains(text)) {
                    list.add(text);
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
}
