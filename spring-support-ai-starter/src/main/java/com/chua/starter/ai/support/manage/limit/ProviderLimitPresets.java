package com.chua.starter.ai.support.manage.limit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 官方限制登记表：数据来自 {@code provider-limits/provider-limit-docs.json}，不再逐家写死在 Java 里。
 *
 * <p>登记表覆盖前端模板库的全部提供商（200+ 家），每条包含：</p>
 * <ul>
 *   <li>{@code aliases}：业务 provider id 与 models.dev 厂商键，按精确匹配；</li>
 *   <li>{@code hosts}：模板库默认地址的主机名，按「相等或子域」匹配；</li>
 *   <li>{@code doc}：厂商官方文档入口（models.dev 目录的 {@code doc} 字段）；</li>
 *   <li>{@code limits} / {@code notes} / {@code verified}：只有核对过官方原文的条目才有数值或计费口径。</li>
 * </ul>
 *
 * <p>匹配顺序：先精确命中 {@code aliases}（一家一个键，不会串行），再按主机名匹配。
 * 前面条目优先，因此精选条目写在文件最前，能压过同键的通用条目。</p>
 *
 * <p>{@link #find} 只返回「有可展示口径」的条目；只有文档入口、没有数值的条目
 * 由 {@link #docUrl} 单独回传，避免把「有文档」说成「有限制」。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class ProviderLimitPresets {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderLimitPresets.class);

    /**
     * 登记表资源路径
     */
    private static final String RESOURCE = "/provider-limits/provider-limit-docs.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 懒加载的登记表；加载失败时为只含空列表的哨兵，避免反复读盘
     */
    private static volatile List<Entry> entries;

    private ProviderLimitPresets() {
    }

    /**
     * 一条登记项。
     *
     * @param id       厂商键
     * @param name     展示名
     * @param aliases  精确匹配的 provider id
     * @param hosts    主机名匹配
     * @param doc      官方文档入口（可为 null）
     * @param verified 数值 / 计费口径是否按官方原文核对过
     * @param limits   已核对的口径（下划线键，可空）
     * @param notes    补充说明
     */
    public record Entry(String id,
                        String name,
                        List<String> aliases,
                        List<String> hosts,
                        String doc,
                        boolean verified,
                        Map<String, Object> limits,
                        String notes) {

        /**
         * 转成限制快照；没有数值 / 计费口径时返回的对象 {@link ProviderLimit#hasValues()} 为 false。
         *
         * @return 限制快照
         */
        public ProviderLimit toLimit() {
            Map<String, Object> source = limits == null ? Map.of() : limits;
            ProviderLimit.ProviderLimitBuilder builder = ProviderLimit.builder()
                    .kind("doc")
                    .source(id)
                    .sourceUrl(doc)
                    .verified(verified)
                    .concurrency(intOf(source, "concurrency"))
                    .requestsPerMinute(intOf(source, "requests_per_minute"))
                    .requestsPerHour(intOf(source, "requests_per_hour"))
                    .requestsPerDay(intOf(source, "requests_per_day"))
                    .tokensPerMinute(intOf(source, "tokens_per_minute"))
                    .tokensPerDay(intOf(source, "tokens_per_day"))
                    .monthlyRequestQuota(longOf(source, "monthly_request_quota"))
                    .monthlyTokenQuota(longOf(source, "monthly_token_quota"))
                    .billingBasis(textOf(source, "billing_basis"))
                    .currency(textOf(source, "currency"))
                    .notes(notes)
                    .capturedAt(System.currentTimeMillis());
            Map<String, Integer> perModel = mapOfInt(source.get("model_concurrency"));
            if (!perModel.isEmpty()) {
                builder.modelConcurrency(perModel);
            }
            return builder.build();
        }
    }

    /**
     * 按平台 / 地址查限制快照。
     *
     * @param platform 平台类型（provider id）
     * @param baseUrl  上游基础地址
     * @return 限制快照；未登记或只有文档入口没有数值时返回 null
     */
    public static ProviderLimit find(String platform, String baseUrl) {
        Entry entry = entryOf(platform, baseUrl);
        if (entry == null) {
            return null;
        }
        ProviderLimit limit = entry.toLimit();
        return limit.hasValues() ? limit : null;
    }

    /**
     * 按平台 / 地址查登记项。
     *
     * @param platform 平台类型（provider id）
     * @param baseUrl  上游基础地址
     * @return 登记项；未登记返回 null
     */
    public static Entry entryOf(String platform, String baseUrl) {
        String id = normalize(platform);
        String host = hostOf(baseUrl);
        for (Entry entry : entries()) {
            if (!id.isEmpty() && entry.aliases().contains(id)) {
                return entry;
            }
        }
        if (host.isEmpty()) {
            return null;
        }
        for (Entry entry : entries()) {
            for (String candidate : entry.hosts()) {
                if (host.equals(candidate) || host.endsWith("." + candidate)) {
                    return entry;
                }
            }
        }
        return null;
    }

    /**
     * 官方文档入口（只有文档、没有数值时也能拿到）。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 文档地址；未登记或无文档返回 null
     */
    public static String docUrl(String platform, String baseUrl) {
        Entry entry = entryOf(platform, baseUrl);
        return entry == null ? null : entry.doc();
    }

    /**
     * 命中的登记者标识（用于报告里说明文档来源）。
     *
     * @param platform 平台类型
     * @param baseUrl  上游基础地址
     * @return 厂商键；未登记返回 null
     */
    public static String docSource(String platform, String baseUrl) {
        Entry entry = entryOf(platform, baseUrl);
        return entry == null ? null : entry.id();
    }

    /**
     * 登记条目数。
     *
     * @return 条目数
     */
    public static int size() {
        return entries().size();
    }

    /**
     * 全部登记项（只读）。
     *
     * @return 登记项
     */
    public static List<Entry> entries() {
        List<Entry> current = entries;
        if (current != null) {
            return current;
        }
        return load();
    }

    /**
     * 读取登记表；失败时返回空列表并留一条告警，不影响同步主流程。
     *
     * @return 登记项
     */
    private static synchronized List<Entry> load() {
        if (entries != null) {
            return entries;
        }
        List<Entry> loaded = new ArrayList<>(256);
        try (InputStream in = ProviderLimitPresets.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOG.warn("[ProviderLimit] 登记表缺失：{}", RESOURCE);
            } else {
                JsonNode root = MAPPER.readTree(in);
                for (JsonNode node : root.path("entries")) {
                    Entry entry = parse(node);
                    if (entry != null) {
                        loaded.add(entry);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("[ProviderLimit] 登记表解析失败，回退到响应头 / 手动：{}", e.toString());
            loaded.clear();
        }
        entries = Collections.unmodifiableList(loaded);
        LOG.info("[ProviderLimit] 登记表 {} 条，官方文档入口 {} 条",
                entries.size(), entries.stream().filter(e -> e.doc() != null).count());
        return entries;
    }

    /**
     * 解析单条登记项。
     *
     * @param node JSON 节点
     * @return 登记项；缺 id 时返回 null
     */
    private static Entry parse(JsonNode node) {
        String id = node.path("id").asText("").trim();
        if (id.isEmpty()) {
            return null;
        }
        List<String> aliases = strings(node.path("aliases"));
        if (!aliases.contains(id)) {
            aliases.add(id);
        }
        String doc = node.path("doc").isTextual() ? node.path("doc").asText().trim() : null;
        Map<String, Object> limits = MAPPER.convertValue(node.path("limits"), Map.class);
        return new Entry(id,
                node.path("name").asText(id),
                List.copyOf(aliases),
                List.copyOf(strings(node.path("hosts"))),
                doc == null || doc.isEmpty() ? null : doc,
                node.path("verified").asBoolean(false),
                limits == null ? Map.of() : limits,
                node.path("notes").isTextual() ? node.path("notes").asText() : null);
    }

    /**
     * 读字符串数组。
     *
     * @param node 数组节点
     * @return 小写去空后的字符串
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
     * 归一化 provider id / 主机名：小写去空白。
     *
     * @param value 原值
     * @return 归一化结果
     */
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 提取 URL 主机名（小写，失败返回空串）。
     *
     * @param url 地址
     * @return 主机名
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
     * 读整数字段。
     *
     * @param map 口径
     * @param key 键
     * @return 值；缺失或非数字返回 null
     */
    private static Integer intOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    /**
     * 读长整数字段。
     *
     * @param map 口径
     * @param key 键
     * @return 值；缺失或非数字返回 null
     */
    private static Long longOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    /**
     * 读文本字段。
     *
     * @param map 口径
     * @param key 键
     * @return 文本；缺失返回 null
     */
    private static String textOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    /**
     * 读「模型名 → 并发上限」。
     *
     * @param raw 原值
     * @return 有序映射；非对象返回空
     */
    private static Map<String, Integer> mapOfInt(Object raw) {
        Map<String, Integer> out = new LinkedHashMap<>(4);
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() instanceof Number number) {
                    out.put(String.valueOf(entry.getKey()), number.intValue());
                }
            }
        }
        return out;
    }
}
