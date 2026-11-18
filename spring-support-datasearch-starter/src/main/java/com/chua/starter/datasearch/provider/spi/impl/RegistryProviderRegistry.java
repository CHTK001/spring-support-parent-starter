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
import java.util.Set;

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
     * 官网统一用 HTTPS
     */
    private static final String HTTPS = "https://";

    /**
     * 复合顶级域：这些后缀占两段（{@code dashscope.aliyun.com.cn} 的主域是
     * {@code aliyun.com.cn}，不是 {@code com.cn}）
     */
    private static final Set<String> MULTI_SUFFIX = Set.of(
            "com.cn", "net.cn", "org.cn", "gov.cn", "edu.cn",
            "com.hk", "com.tw", "com.sg", "com.au", "com.br",
            "co.uk", "org.uk", "ac.uk", "co.jp", "co.kr", "co.nz");

    /**
     * 不能当官网的本机地址
     */
    private static final Set<String> LOCAL_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "::1");

    /**
     * 渠道标识的能力前缀（{@code chat-duckduckgo} / {@code image-pollinations} /
     * {@code video-doubao} / {@code model3d-forge}）：登记表按厂商标识登记。
     */
    private static final Set<String> KIND_PREFIXES = Set.of(
            "chat", "image", "video", "model3d", "3d", "text", "audio", "embedding", "rerank");

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
        ProviderDescriptor hit = aliasOf(current, id);
        if (hit != null) {
            return hit;
        }
        // 渠道实现报的是「能力前缀 + 厂商标识」（chat-duckduckgo / image-pollinations /
        // video-doubao / model3d-forge），登记表按厂商标识登记。去掉这一层前缀再命中一次，
        // 官网 / 文档这类登记表数据才不会对渠道页整片落空。前缀是封闭集合，不做模糊匹配。
        String bare = stripKindPrefix(id);
        if (!bare.equals(id)) {
            hit = aliasOf(current, bare);
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

    /**
     * 按 id / 别名查登记项。
     *
     * @param current 登记表
     * @param id      已归一化的标识
     * @return 登记项；未命中返回 null
     */
    private static ProviderDescriptor aliasOf(Snapshot current, String id) {
        return id.isEmpty() ? null : current.byAlias.get(id);
    }

    /**
     * 去掉渠道标识的能力前缀（{@code chat-duckduckgo → duckduckgo}）。
     *
     * <p>前缀集合与渠道 SPI 实际使用的命名一致；不在集合里的原样返回，
     * 避免把 {@code qwen-max} 这类自带连字符的标识误伤。</p>
     *
     * @param id 已归一化的标识
     * @return 去掉一层能力前缀后的标识
     */
    private static String stripKindPrefix(String id) {
        int dash = id.indexOf('-');
        if (dash <= 0 || dash + 1 >= id.length()) {
            return id;
        }
        return KIND_PREFIXES.contains(id.substring(0, dash)) ? id.substring(dash + 1) : id;
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
                    // 变体：同一家厂商的另一个接入端点（如火山方舟 /api/v3 与
                    // Coding Plan /api/coding/v3）。继承主条目缺省、只覆盖差异字段，
                    // 仍按自己的 id / 别名命中，既有渠道按原 id 解析拿到的还是原端点；
                    // 主机名相同时主条目先登记，按域名匹配落到主条目
                    for (JsonNode variantNode : node.path("variants")) {
                        ProviderDescriptor variant = parseVariant(variantNode, entry);
                        if (variant == null) {
                            continue;
                        }
                        loaded.entries.add(variant);
                        loaded.byAlias.putIfAbsent(normalize(variant.getId()), variant);
                        for (String host : variant.getHosts() == null ? List.<String>of() : variant.getHosts()) {
                            if (!host.isEmpty()) {
                                loaded.byHost.putIfAbsent(host, variant);
                            }
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
        entry.setWebsite(websiteOf(node, entry.getBaseUrl(), entry.getDoc()));
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
     * 解析变体登记项（同一家厂商的另一个接入端点）。
     *
     * <p>变体只写与主条目不同的字段（如火山 Coding Plan 的独立地址与文档），
     * 其余继承主条目：名称、协议、主机名、官网、能力位、余额口径、限制口径。
     * 变体仍按自己的 id / 别名命中，缺省地址用变体自己的，保证既有渠道按原 id
     * 解析时拿到的还是原来的端点。</p>
     *
     * @param node   变体节点
     * @param parent 主条目
     * @return 变体登记项；缺 id 返回 null
     */
    private static ProviderDescriptor parseVariant(JsonNode node, ProviderDescriptor parent) {
        String id = node.path("id").asText("").trim();
        if (id.isEmpty()) {
            return null;
        }
        ProviderDescriptor entry = new ProviderDescriptor();
        entry.setId(id);
        entry.setName(text(node, "name", parent.getName()));
        entry.setDescription(text(node, "description", parent.getDescription()));
        entry.setProtocol(text(node, "protocol", parent.getProtocol()));
        entry.setBaseUrl(text(node, "baseUrl", parent.getBaseUrl()));
        entry.setDoc(text(node, "doc", parent.getDoc()));
        String website = text(node, "website", null);
        if (website == null) {
            // 主条目显式登记的官网最权威（火山等厂商主站不在 API 域下，推导会错到
            // volces.com 这类模型域名）；推导只在没有可继承值时兜底
            website = parent.getWebsite();
        }
        if (website == null) {
            website = websiteOf(node, entry.getBaseUrl(), entry.getDoc());
        }
        entry.setWebsite(website);
        entry.setModelsDevId(text(node, "modelsDevId", parent.getModelsDevId()));
        List<String> aliases = strings(node.path("aliases"));
        entry.setAliases(aliases.isEmpty() ? List.of(id) : aliases);
        List<String> hosts = strings(node.path("hosts"));
        entry.setHosts(hosts.isEmpty() ? parent.getHosts() : hosts);
        JsonNode capabilities = node.path("capabilities");
        entry.setCapability(capabilities.isMissingNode() || capabilities.isNull()
                ? parent.getCapability() : capability(capabilities));
        JsonNode credit = node.path("credit");
        if (credit.isMissingNode() || credit.isNull()) {
            entry.setCreditMode(parent.getCreditMode());
            entry.setCreditKind(parent.getCreditKind());
            entry.setCreditProviderId(parent.getCreditProviderId());
            entry.setCreditCurrency(parent.getCreditCurrency());
        } else {
            entry.setCreditMode(text(credit, "mode", null));
            entry.setCreditKind(text(credit, "kind", null));
            entry.setCreditProviderId(text(credit, "providerId", null));
            entry.setCreditCurrency(text(credit, "currency", null));
        }
        JsonNode limits = node.path("limits");
        entry.setLimitsVerified(limits.isMissingNode() || limits.isNull()
                ? parent.isLimitsVerified() : limits.path("verified").asBoolean(false));
        return entry;
    }

    /**
     * 解析官网：显式登记优先，其次取上游地址 / 官方文档站的可注册主域。
     *
     * <p>之所以要推：登记表 213 家来自前端模板库，模板只写「API 地址」，没有官网；
     * 手写 213 条既容易写错又会随模板增删漂移。这里用两个稳定事实推导——
     * 上游地址与官方文档站都必然落在该厂商的域下，取可注册主域
     * （{@code api.openai.com → openai.com}、{@code llm.chutes.ai → chutes.ai}）
     * 就是官网；功能性前缀（{@code api.} / {@code docs.} / {@code llm.} …）不逐个列举，
     * 免得漏一个就得到一个打不开的地址。</p>
     *
     * <p>先上游地址、后文档站：上游地址更能代表用户正在接入的那家，文档站常挂
     * 在国际站域名下（如国内厂商的 {@code platform.xxx.ai}）。</p>
     *
     * <p>推不出来就返回 {@code null}（本地推理、路由组、自建地址这些没有对外站点），
     * 不拿 API 地址顶替——{@code https://api.openai.com} 打开是 404，不是官网。
     * 个别厂商的主站不在 API 域下（AWS、Azure、火山、百炼等），在
     * {@code provider-registry.json} 里写显式 {@code website} 覆盖，不走推导。</p>
     *
     * @param node    登记项节点
     * @param baseUrl 上游基础地址（可空）
     * @param doc     官方文档入口（可空）
     * @return 官网地址；推不出返回 null
     */
    private static String websiteOf(JsonNode node, String baseUrl, String doc) {
        String explicit = text(node, "website", null);
        if (explicit != null) {
            return explicit;
        }
        String fromBase = siteHost(baseUrl);
        if (fromBase != null) {
            return HTTPS + fromBase;
        }
        String fromDoc = siteHost(doc);
        return fromDoc == null ? null : HTTPS + fromDoc;
    }

    /**
     * 取地址的可注册主域（官网候选）。
     *
     * @param url 地址（可空）
     * @return 主域主机名；本机 / 单段主机名 / 非法地址返回 null
     */
    private static String siteHost(String url) {
        String host = hostOf(url);
        if (host.isEmpty() || !isPublicHost(host)) {
            return null;
        }
        String site = registrableHost(host);
        return isPublicHost(site) ? site : null;
    }

    /**
     * 取可注册主域：通用顶级域取末两段，复合后缀（{@code com.cn} / {@code co.uk} …）
     * 取末三段。
     *
     * @param host 主机名
     * @return 可注册主域
     */
    private static String registrableHost(String host) {
        String[] labels = host.split("\\.");
        if (labels.length <= 2) {
            return host;
        }
        String lastTwo = labels[labels.length - 2] + "." + labels[labels.length - 1];
        int keep = MULTI_SUFFIX.contains(lastTwo) ? 3 : 2;
        if (labels.length <= keep) {
            return host;
        }
        StringBuilder site = new StringBuilder(host.length());
        for (int i = labels.length - keep; i < labels.length; i++) {
            if (site.length() > 0) {
                site.append('.');
            }
            site.append(labels[i]);
        }
        return site.toString();
    }

    /**
     * 是否是能对外打开的站点主机（排除本机与单段主机名）。
     *
     * @param host 主机名
     * @return 可用于官网返回 true
     */
    private static boolean isPublicHost(String host) {
        if (host == null || host.indexOf('.') < 0) {
            return false;
        }
        if (LOCAL_HOSTS.contains(host)) {
            return false;
        }
        return !host.endsWith(".local");
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
