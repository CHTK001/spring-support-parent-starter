package com.chua.starter.ai.support.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 官网技能市场（ai.dingd.cn）来源实现。
 *
 * <p>从官网市场接口读取精选技能（含 ZIP/Git 安装地址），由后端聚合下发；
 * 列表按 {@link #CACHE_TTL} 短时缓存，关键词在内存中过滤。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
public class DingdSkillMarketProvider implements SkillMarketProvider {

    /**
     * 来源标识
     */
    public static final String CODE = "dingd";

    /**
     * 市场数据接口
     */
    private static final String MARKET_URL = "https://ai.dingd.cn/api/market/skills";

    /**
     * ZIP 兜底地址模板
     */
    private static final String ZIP_FALLBACK = "https://ai.dingd.cn/downloads/skills/%s/%s.zip";

    /**
     * 缓存有效期
     */
    private static final long CACHE_TTL = Duration.ofMinutes(10).toMillis();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private volatile List<SkillMarketEntry> cache = Collections.emptyList();
    private volatile long cacheAt;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public List<SkillMarketEntry> list(String keyword) {
        List<SkillMarketEntry> all = ensureCache();
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (kw.isEmpty()) {
            return all;
        }
        List<SkillMarketEntry> matched = new ArrayList<>();
        for (SkillMarketEntry e : all) {
            String name = e.name() == null ? "" : e.name().toLowerCase(Locale.ROOT);
            String desc = e.description() == null ? "" : e.description().toLowerCase(Locale.ROOT);
            if (name.contains(kw) || desc.contains(kw)) {
                matched.add(e);
            }
        }
        return matched;
    }

    private List<SkillMarketEntry> ensureCache() {
        List<SkillMarketEntry> snapshot = cache;
        if (snapshot != null && !snapshot.isEmpty() && System.currentTimeMillis() - cacheAt < CACHE_TTL) {
            return snapshot;
        }
        synchronized (this) {
            snapshot = cache;
            if (snapshot != null && !snapshot.isEmpty() && System.currentTimeMillis() - cacheAt < CACHE_TTL) {
                return snapshot;
            }
            snapshot = fetch();
            cache = snapshot;
            cacheAt = System.currentTimeMillis();
            return snapshot;
        }
    }

    private List<SkillMarketEntry> fetch() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(MARKET_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[SkillMarket:{}] 拉取失败 status={}", CODE, response.statusCode());
                return cache == null ? Collections.emptyList() : cache;
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<SkillMarketEntry> entries = new ArrayList<>();
            if (root != null && root.isArray()) {
                for (JsonNode node : root) {
                    SkillMarketEntry entry = mapNode(node);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }
            log.info("[SkillMarket:{}] 拉取到 {} 个技能", CODE, entries.size());
            return entries;
        } catch (Exception e) {
            log.warn("[SkillMarket:{}] 拉取异常: {}", CODE, e.getMessage());
            return cache == null ? Collections.emptyList() : cache;
        }
    }

    private SkillMarketEntry mapNode(JsonNode node) {
        if (node == null || node.path("id").asText(null) == null) {
            return null;
        }
        String id = node.path("id").asText();

        List<String> labels = new ArrayList<>();
        JsonNode labelNode = node.get("labels");
        if (labelNode != null && labelNode.isArray()) {
            for (JsonNode l : labelNode) {
                labels.add(l.asText());
            }
        }

        String zipUrl = null;
        String gitUrl = null;
        JsonNode installNode = node.get("installUrls");
        if (installNode != null && installNode.isArray()) {
            for (JsonNode u : installNode) {
                String type = u.path("type").asText();
                String url = u.path("url").asText(null);
                if (url == null) {
                    continue;
                }
                if ("zip".equalsIgnoreCase(type)) {
                    zipUrl = url;
                } else if ("git".equalsIgnoreCase(type)) {
                    gitUrl = url;
                }
            }
        }
        if (zipUrl == null) {
            zipUrl = String.format(ZIP_FALLBACK, id, id);
        }

        return new SkillMarketEntry(
                id,
                node.path("name").asText(id),
                node.path("description").asText(null),
                node.has("icon") && !node.path("icon").isNull() ? node.path("icon").asText(null) : null,
                labels,
                zipUrl,
                gitUrl,
                node.path("detailUrl").asText(null),
                node.path("version").asText(null),
                node.path("recommended").asBoolean(false));
    }
}
