package com.chua.starter.datasearch.provider.spi.impl;

import com.chua.starter.datasearch.provider.model.PricingSourceConfig;
import com.chua.starter.datasearch.provider.model.ProviderPricingRefresh;
import com.chua.starter.datasearch.provider.model.ProviderPricingRule;
import com.chua.starter.datasearch.provider.spi.ProviderPricingProvider;
import com.chua.starter.datasearch.provider.support.DeepSeekPricingSource;
import com.chua.starter.datasearch.provider.support.MoonshotPricingSource;
import com.chua.starter.datasearch.provider.support.PricingPageSource;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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
 * 定价规则登记表实现：读 {@code provider-catalog/provider-pricing-rules.json}。
 *
 * <p>只收录官方价格页能核对到的峰谷 / 按次 / 按 N 次口径；目录里的标准价仍由
 * models.dev 快照提供，两者互补。匹配顺序与限制登记表一致：先精确命中别名，
 * 再按主机名（相等或子域）匹配。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
@Slf4j
public class RegistryProviderPricingProvider implements ProviderPricingProvider {

    private static final String RESOURCE = "/provider-catalog/provider-pricing-rules.json";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static volatile List<Entry> cached;

    @Override
    public String name() {
        return "registry";
    }

    @Override
    public boolean supports(String platform, String baseUrl) {
        return find(platform, baseUrl) != null;
    }

    @Override
    public List<ProviderPricingRule> query(String platform, String baseUrl) {
        Entry entry = find(platform, baseUrl);
        return entry == null ? Collections.emptyList() : entry.rules;
    }

    /**
     * 点击同步时的实时刷新。
     *
     * <p>DeepSeek / Moonshot 有专用解析器；其余厂商只要登记项里带 {@code source}
     * （官方 URL + 通用读取方式）就走 {@link PricingPageSource}，不再为每家新增 Java 分支。</p>
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 刷新结果
     */
    @Override
    public ProviderPricingRefresh refresh(String platform, String baseUrl) {
        Entry entry = find(platform, baseUrl);
        if (entry == null) {
            return ProviderPricingRefresh.unsupported();
        }
        if (entry.source != null) {
            ProviderPricingRefresh refresh = PricingPageSource.fetch(entry.source, entry.doc);
            if (refresh.isSupported() && refresh.getRules() != null) {
                for (ProviderPricingRule rule : refresh.getRules()) {
                    rule.setProviderId(entry.id);
                    if (rule.getSourceUrl() == null || rule.getSourceUrl().isBlank()) {
                        rule.setSourceUrl(entry.doc);
                    }
                }
            }
            return refresh;
        }
        if ("deepseek".equalsIgnoreCase(entry.id)) {
            return DeepSeekPricingSource.fetch(entry.doc);
        }
        if ("moonshot".equalsIgnoreCase(entry.id)) {
            return MoonshotPricingSource.fetch(entry.doc);
        }
        return ProviderPricingRefresh.unsupported();
    }

    @Override
    public int size() {
        int total = 0;
        for (Entry entry : entries()) {
            total += entry.rules.size();
        }
        return total;
    }

    /**
     * 查登记项。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 登记项；未收录返回 null
     */
    private Entry find(String platform, String baseUrl) {
        String id = normalize(platform);
        String host = hostOf(baseUrl);
        if (!id.isEmpty()) {
            for (Entry entry : entries()) {
                if (entry.aliases.contains(id)) {
                    return entry;
                }
            }
        }
        if (host.isEmpty()) {
            return null;
        }
        for (Entry entry : entries()) {
            for (String candidate : entry.hosts) {
                if (host.equals(candidate) || host.endsWith("." + candidate)) {
                    return entry;
                }
            }
        }
        return null;
    }

    /**
     * 读取登记表（懒加载一次）。
     *
     * @return 登记项
     */
    private static List<Entry> entries() {
        List<Entry> current = cached;
        if (current != null) {
            return current;
        }
        return load();
    }

    /**
     * 解析登记表；失败时返回空列表，不影响主流程。
     *
     * @return 登记项
     */
    private static synchronized List<Entry> load() {
        if (cached != null) {
            return cached;
        }
        List<Entry> loaded = new ArrayList<>(4);
        try (InputStream in = RegistryProviderPricingProvider.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("[provider] 定价登记表缺失: {}", RESOURCE);
            } else {
                JsonNode root = MAPPER.readTree(in);
                for (JsonNode node : root.path("entries")) {
                    Entry entry = parse(node);
                    // 允许「只有官方价格页、没有静态规则」的实时抓取项（如 Moonshot）：
                    // 规则在点击同步时抓，不能因为 rules 为空就把整个登记项丢掉。
                    if (entry != null && (!entry.rules.isEmpty() || entry.doc != null
                            || entry.source != null || !entry.hosts.isEmpty())) {
                        loaded.add(entry);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[provider] 定价登记表解析失败: {}", e.toString());
            loaded.clear();
        }
        cached = Collections.unmodifiableList(loaded);
        log.info("[provider] 定价登记表 {} 家、{} 条规则", cached.size(),
                cached.stream().mapToInt(e -> e.rules.size()).sum());
        return cached;
    }

    /**
     * 解析一家厂商的定价条目。
     *
     * @param node JSON 节点
     * @return 登记项；缺 id 或规则为空返回 null
     */
    private static Entry parse(JsonNode node) {
        String id = node.path("id").asText("").trim();
        if (id.isEmpty()) {
            return null;
        }
        Entry entry = new Entry();
        entry.id = id;
        entry.name = node.path("name").asText(id);
        entry.doc = node.path("doc").isTextual() ? node.path("doc").asText() : null;
        if (node.path("source").isObject()) {
            entry.source = MAPPER.convertValue(node.path("source"), PricingSourceConfig.class);
        }
        entry.aliases = strings(node.path("aliases"));
        entry.hosts = strings(node.path("hosts"));
        boolean verified = node.path("verified").asBoolean(false);
        for (JsonNode ruleNode : node.path("rules")) {
            ProviderPricingRule rule = MAPPER.convertValue(ruleNode, ProviderPricingRule.class);
            if (rule != null) {
                rule.setProviderId(id);
                rule.setVerified(verified);
                if (rule.getSourceUrl() == null) {
                    rule.setSourceUrl(entry.doc);
                }
                entry.rules.add(rule);
            }
        }
        entry.rules = Collections.unmodifiableList(entry.rules);
        return entry;
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

    /**
     * 一家厂商的登记项。
     */
    private static final class Entry {

        /**
         * 厂商键
         */
        private String id;

        /**
         * 展示名
         */
        private String name;

        /**
         * 官方价格页
         */
        private String doc;

        /**
         * 通用价格页读取配置
         */
        private PricingSourceConfig source;

        /**
         * 别名
         */
        private List<String> aliases = new ArrayList<>(4);

        /**
         * 主机名
         */
        private List<String> hosts = new ArrayList<>(4);

        /**
         * 定价规则
         */
        private List<ProviderPricingRule> rules = new ArrayList<>(4);
    }
}
