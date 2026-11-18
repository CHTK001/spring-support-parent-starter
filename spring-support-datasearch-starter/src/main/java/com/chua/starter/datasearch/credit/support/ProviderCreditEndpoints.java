package com.chua.starter.datasearch.credit.support;

import com.chua.starter.datasearch.credit.model.ProviderCreditEndpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 官方余额接口登记表：读 {@code provider-catalog/provider-credit-endpoints.json}。
 *
 * <p>数据由原 {@code HostCreditQueryProvider} 的 15 条预设迁移而来，现含 18 家 Bearer 口径
 * + 6 家账户级 AK/SK 签名（阿里云 / 火山 / 百度 / 七牛 / 腾讯云 / 华为云），新增一家只改 JSON。
 * 这里只负责装载、按厂商标识 / 主机名查找与同源判断，不发请求。</p>
 *
 * <p>同源判断的必要性：余额接口必须与 Key 同源。中转站的 Key 拿去问厂商官方接口只会
 * 拿到 401，还会把「Key 无效」写进页面，所以先按域名比对，不一致就跳过官方候选。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@Slf4j
public final class ProviderCreditEndpoints {

    /**
     * 登记表资源路径
     */
    private static final String RESOURCE = "/provider-catalog/provider-credit-endpoints.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 已解析的登记表
     */
    private static volatile Snapshot snapshot;

    private ProviderCreditEndpoints() {
    }

    /**
     * 全部登记项。
     *
     * @return 登记项（文件顺序）
     */
    public static List<ProviderCreditEndpoint> entries() {
        return snapshot().entries;
    }

    /**
     * 登记条目数。
     *
     * @return 条目数
     */
    public static int size() {
        return snapshot().entries.size();
    }

    /**
     * 按厂商标识查登记项。
     *
     * @param providerId 厂商标识
     * @return 登记项；未收录返回 null
     */
    public static ProviderCreditEndpoint find(String providerId) {
        String id = normalize(providerId);
        if (id.isEmpty()) {
            return null;
        }
        return snapshot().byAlias.get(id);
    }

    /**
     * 按主机名查登记项（相等或子域）。
     *
     * @param host 主机名或地址
     * @return 登记项；未命中返回 null
     */
    public static ProviderCreditEndpoint byHost(String host) {
        String value = host == null || host.contains("://") ? normalize(hostOf(host)) : normalize(host);
        if (value.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, ProviderCreditEndpoint> entry : snapshot().byHost.entrySet()) {
            String candidate = entry.getKey();
            if (value.equals(candidate) || value.endsWith("." + candidate)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 全部已收录的厂商标识（含 AK/SK 签名类）。
     *
     * @return 厂商标识集合（只读）
     */
    public static Set<String> brands() {
        return snapshot().brands;
    }

    /**
     * Bearer 类厂商标识（不含 AK/SK 签名类）。
     *
     * <p>走 API Key 逐个查余额的场景（如 AI 供应商面板）拿不到 AK/SK，只能用这一份；
     * 签名类命中只会多一条必然失败的候选。</p>
     *
     * @return 厂商标识集合（只读）
     */
    public static Set<String> bearerBrands() {
        return snapshot().bearerBrands;
    }

    /**
     * 是否收录了「Bearer 类」厂商的余额端点。
     *
     * @param providerId 厂商标识
     * @return 是 Bearer 类预设为 true
     */
    public static boolean supportsHost(String providerId) {
        ProviderCreditEndpoint endpoint = find(providerId);
        return endpoint != null && endpoint.getSignType() == null;
    }

    /**
     * 预设域名是否与 Key 所属站点一致。
     *
     * <p>上游地址为空（用户只选了厂商）视为一致；域名相同或互为子域视为一致；
     * 同一厂商常用不同子域（调用走 router.requesty.ai、管理面走 api-v2.requesty.ai），
     * 按主域再放行一次。其余一律不一致。</p>
     *
     * @param providerId 厂商标识
     * @param baseUrl    供应商配置里的上游地址
     * @return 一致或无法判断时 {@code true}
     */
    public static boolean sameOrigin(String providerId, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return true;
        }
        ProviderCreditEndpoint endpoint = find(providerId);
        if (endpoint == null) {
            return false;
        }
        String preset = hostOf(endpoint.getUrl());
        String actual = hostOf(baseUrl);
        if (preset == null || actual == null) {
            return true;
        }
        if (preset.equals(actual) || preset.endsWith("." + actual) || actual.endsWith("." + preset)) {
            return true;
        }
        String presetDomain = baseDomain(preset);
        return presetDomain != null && presetDomain.equals(baseDomain(actual));
    }

    /**
     * 取地址的域名。
     *
     * @param url 地址
     * @return 小写域名；地址非法或缺失时为 {@code null}
     */
    public static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 取主域（{@code api-v2.requesty.ai → requesty.ai}）。
     *
     * @param host 域名
     * @return 主域；无法判断时返回原值
     */
    private static String baseDomain(String host) {
        if (host == null) {
            return null;
        }
        String[] parts = host.split("\\.");
        if (parts.length <= 2) {
            return host;
        }
        String second = parts[parts.length - 2];
        boolean compound = parts[parts.length - 1].length() == 2
                && ("com".equals(second) || "net".equals(second) || "org".equals(second)
                || "gov".equals(second) || "edu".equals(second));
        int keep = compound ? 3 : 2;
        if (parts.length <= keep) {
            return host;
        }
        return String.join(".", java.util.Arrays.copyOfRange(parts, parts.length - keep, parts.length));
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
        try (InputStream in = ProviderCreditEndpoints.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("[credit] 余额接口登记表缺失: {}", RESOURCE);
            } else {
                JsonNode root = MAPPER.readTree(in);
                for (JsonNode node : root.path("entries")) {
                    ProviderCreditEndpoint endpoint = parse(node);
                    if (endpoint == null) {
                        continue;
                    }
                    loaded.entries.add(endpoint);
                    for (String alias : endpoint.getAliases()) {
                        loaded.byAlias.putIfAbsent(alias, endpoint);
                    }
                    loaded.byAlias.putIfAbsent(normalize(endpoint.getId()), endpoint);
                    for (String host : endpoint.getHosts()) {
                        loaded.byHost.putIfAbsent(host, endpoint);
                    }
                    loaded.brands.add(endpoint.getId());
                    if (endpoint.getSignType() == null) {
                        loaded.bearerBrands.add(endpoint.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[credit] 余额接口登记表解析失败: {}", e.toString());
            loaded.entries.clear();
        }
        loaded.entries = Collections.unmodifiableList(loaded.entries);
        loaded.brands = Collections.unmodifiableSet(loaded.brands);
        loaded.bearerBrands = Collections.unmodifiableSet(loaded.bearerBrands);
        snapshot = loaded;
        log.info("[credit] 余额接口登记表 {} 条，Bearer {} 条，签名 {} 条",
                loaded.entries.size(), loaded.bearerBrands.size(),
                loaded.brands.size() - loaded.bearerBrands.size());
        return loaded;
    }

    /**
     * 解析单条登记项。
     *
     * @param node JSON 节点
     * @return 登记项；缺 id / url 返回 null（表达式可缺省：字段换算由专属实现负责时，
     *         如 opencode-console 的 microCents ÷ 1e8，登记表表达式装不下）
     */
    private static ProviderCreditEndpoint parse(JsonNode node) {
        String id = normalize(node.path("id").asText(""));
        String url = node.path("url").asText("").trim();
        if (id.isEmpty() || url.isEmpty()) {
            return null;
        }
        ProviderCreditEndpoint endpoint = new ProviderCreditEndpoint();
        endpoint.setId(id);
        endpoint.setName(node.path("name").asText(id));
        endpoint.setAliases(strings(node.path("aliases"), id));
        endpoint.setHosts(strings(node.path("hosts"), null));
        endpoint.setUrl(url);
        // 表达式缺省时走登记表解析会得到「缺少表达式」的明确失败，不会静默出错
        endpoint.setExpression(node.path("expression").asText("").trim());
        endpoint.setCurrency(text(node, "currency"));
        endpoint.setSignType(text(node, "signType"));
        endpoint.setMethod(text(node, "method"));
        endpoint.setAuthHeader(text(node, "authHeader"));
        endpoint.setVerified(node.path("verified").asBoolean(false));
        endpoint.setNotes(text(node, "notes"));
        return endpoint;
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
        return value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }

    /**
     * 读字符串数组（小写去空去重）。
     *
     * @param node     数组节点
     * @param fallback 数组为空时的回退值（可空）
     * @return 值
     */
    private static List<String> strings(JsonNode node, String fallback) {
        List<String> list = new ArrayList<>(4);
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String value = normalize(item.asText(""));
                if (!value.isEmpty() && !list.contains(value)) {
                    list.add(value);
                }
            }
        }
        if (list.isEmpty() && fallback != null) {
            list.add(fallback);
        }
        return list;
    }

    /**
     * 归一化厂商标识 / 主机名。
     *
     * @param value 原值
     * @return 小写去空白
     */
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 登记表索引。
     */
    private static final class Snapshot {

        /**
         * 登记项（文件顺序）
         */
        private List<ProviderCreditEndpoint> entries = new ArrayList<>(16);

        /**
         * 厂商标识 / 别名 → 登记项
         */
        private final Map<String, ProviderCreditEndpoint> byAlias = new LinkedHashMap<>(32);

        /**
         * 主机名 → 登记项
         */
        private final Map<String, ProviderCreditEndpoint> byHost = new LinkedHashMap<>(32);

        /**
         * 全部厂商标识
         */
        private Set<String> brands = new LinkedHashSet<>(16);

        /**
         * Bearer 类厂商标识
         */
        private Set<String> bearerBrands = new LinkedHashSet<>(16);
    }
}
