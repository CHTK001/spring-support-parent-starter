package com.chua.starter.ai.support.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 联网搜索服务。
 *
 * <p>优先使用系统「搜索供应商」配置的付费搜索 API（博查 / Tavily）；
 * 当未配置或 key 无效时，使用 Bing 网页结果兜底（无需 key，国内可直连），
 * 保证「在线检索」始终能拿到真实的实时网页结果。</p>
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchService {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Pattern H2_A = Pattern.compile(
            "<h2[^>]*>\\s*<a[^>]*?href=\"(http[^\"]+)\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern LINE_CLAMP = Pattern.compile(
            "<p class=\"b_lineclamp[^\"]*\"[^>]*>(.*?)</p>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern ANY_P = Pattern.compile(
            "<p>(.*?)</p>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern API_KEY = Pattern.compile(
            "apiKey[\"'\\s:=]+([^}\\s'\"]+)");

    private final ObjectMapper objectMapper;
    private final SysAiGlobalSettingService sysAiGlobalSettingService;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    /** 选中付费源的优先等待窗口：有效且快速返回则优先采用高质量结果，否则立即转用免费 360 */
    private static final long PAID_PREFERRED_MS = 2500L;

    private static final ExecutorService SEARCH_POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "web-search");
        t.setDaemon(true);
        return t;
    });

    /**
     * 联网检索。
     *
     * @param query 查询词，不允许为空白
     * @return 结果列表，无数据时为空列表
     */
    public List<WebResult> search(String query) {
        return search(query, 6);
    }

    /**
     * 把用户口语化提问提炼为适合搜索引擎的核心关键词。
     *
     * <p>去除「帮我 / 请 / 联网 / 搜索 / 查一下」等开头祈使语，以及
     * 「请给出…来源链接 / 谢谢」等结尾要求语，保留核心主题，避免搜索引擎
     * 被口语词（如句首的「帮」字）带偏到字典释义等无关结果。</p>
     *
     * @param raw 原始提问
     * @return 提炼后的检索词；无法提炼时返回去除首尾空白的原文
     */
    public static String refineQuery(String raw) {
        if (raw == null) {
            return "";
        }
        String q = raw.trim();
        // 1. 循环剥离开头的称呼 / 祈使 / 联网 / 检索动词
        String before;
        do {
            before = q;
            q = q.replaceFirst("^[，,、\\s]+", "");
            q = q.replaceFirst("^(你好|您好|请问|请|麻烦|帮我|帮忙|帮|给我)[，,、:：\\s]*", "");
            q = q.replaceFirst("^(帮我|帮忙|帮|给我)?(联网|上网|在线|网上|网络|外网)[上的]?[，,、\\s]*", "");
            q = q.replaceFirst("^(搜索一下|搜一下|查一下|找一下|检索一下|查询一下|搜索|搜一搜|搜|查询|查下|查一查|查|检索|找一找|找找|找)[，,、\\s]*", "");
            q = q.replaceFirst("^(一下|关于|那个|这个|下)[，,、\\s]*", "");
        } while (!q.equals(before) && !q.isBlank());

        if (q.isBlank()) {
            return raw.trim();
        }

        // 2. 按标点切分，从尾部移除"要求 / 礼貌"类非核心片段（疑问片段一律保留）
        String[] parts = q.split("[，,。；;！!？?]");
        List<String> keep = new ArrayList<>();
        for (String p : parts) {
            if (!p.isBlank()) {
                keep.add(p.trim());
            }
        }
        while (!keep.isEmpty()) {
            String last = keep.get(keep.size() - 1);
            boolean isQuestion = last.matches(".*(什么|哪些|多少|几个|几点|怎么|怎样|如何|为何|为什么|是谁|哪里|哪儿|哪个|吗|呢|么|几|啥).*");
            boolean isPoliteReq = last.matches(
                    ".*(请|麻烦).*(给|提供|列出|标注|附上|说明|告诉|发)|"
                  + ".*(谢谢|感谢|辛苦了)|"
                  + ".*(来源链接|参考链接|参考资料|参考文献|引用链接)|"
                  + "^(给|提供|发).*(链接|来源|网址|出处).*");
            if (!isQuestion && isPoliteReq) {
                keep.remove(keep.size() - 1);
            } else {
                break;
            }
        }
        String result = String.join(" ", keep);
        return result.isBlank() ? q : result;
    }

    /**
     * 联网检索。
     *
     * @param query 查询词，不允许为空白
     * @param count 期望结果数量
     * @return 结果列表，无数据时为空列表
     */
    public List<WebResult> search(String query, int count) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        query = refineQuery(query);
        if (query.isBlank()) {
            return List.of();
        }
        final String searchQuery = query;
        String provider = sysAiGlobalSettingService.getValue("search", "provider")
                .map(String::trim).orElse("360");

        boolean selectedPaid = switch (provider) {
            case "bocha", "tavily", "metaso" -> true;
            default -> false;
        };

        // 付费源与免费 360 并行启动，竞速取结果，避免无效/挂起的付费 key 拖慢整体检索
        CompletableFuture<List<WebResult>> paidFut = selectedPaid
                ? CompletableFuture.supplyAsync(() -> safePaid(provider, searchQuery, count), SEARCH_POOL)
                : CompletableFuture.completedFuture(List.of());
        CompletableFuture<List<WebResult>> soFut = CompletableFuture.supplyAsync(
                () -> safeCall(() -> so360(searchQuery, count)), SEARCH_POOL);

        // 1) 选中的付费源在短优先窗口内成功 → 采用高质量付费结果
        try {
            List<WebResult> pr = paidFut.get(PAID_PREFERRED_MS, TimeUnit.MILLISECONDS);
            if (pr != null && !pr.isEmpty()) {
                soFut.cancel(true);
                log.info("[WebSearch] 使用 {} 检索成功，{} 条", provider, pr.size());
                return pr;
            }
        } catch (Exception ignore) {
            // 付费源超时/异常：立即转用 360
        }

        // 2) 采用免费 360（此时通常已并行完成）
        try {
            List<WebResult> sr = soFut.get(12, TimeUnit.SECONDS);
            if (sr != null && !sr.isEmpty()) {
                paidFut.cancel(true);
                log.info("[WebSearch] 使用 360 检索成功，{} 条", sr.size());
                return sr;
            }
        } catch (Exception e) {
            log.warn("[WebSearch] 360 检索异常: {}", e.getMessage());
        }

        // 3) 360 失败：再给付费源机会（可能较慢但有效）
        try {
            List<WebResult> pr = paidFut.get(12, TimeUnit.SECONDS);
            if (pr != null && !pr.isEmpty()) {
                log.info("[WebSearch] 使用 {} 检索成功，{} 条", provider, pr.size());
                return pr;
            }
        } catch (Exception e) {
            log.warn("[WebSearch] 付费源检索异常: {}", e.getMessage());
        }

        // 4) Bing 最终兜底
        try {
            List<WebResult> br = bing(query, count);
            if (!br.isEmpty()) {
                log.info("[WebSearch] 使用 Bing 检索成功，{} 条", br.size());
                return br;
            }
        } catch (Exception e) {
            log.warn("[WebSearch] Bing 兜底失败: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * 安全调用某个付费搜索源，异常或未实现时返回空列表（不抛出）。
     */
    private List<WebResult> safePaid(String p, String query, int count) {
        try {
            return switch (p) {
                case "tavily" -> tavily(query, count);
                case "bocha" -> bocha(query, count);
                // metaso（秘塔）搜索 API 暂未接入，返回空以便竞速快速落到 360
                case "metaso" -> List.of();
                default -> List.of();
            };
        } catch (Exception e) {
            log.warn("[WebSearch] {} 检索失败: {}", p, e.getMessage());
            return List.of();
        }
    }

    /**
     * 安全执行搜索调用，异常时返回空列表。
     */
    private List<WebResult> safeCall(Callable<List<WebResult>> c) {
        try {
            return c.call();
        } catch (Exception e) {
            log.warn("[WebSearch] 检索调用失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 博查搜索。
     */
    private List<WebResult> bocha(String query, int count) throws Exception {
        String key = extractApiKey(sysAiGlobalSettingService.getValue("search", "bocha").orElse(null));
        if (key == null || key.isBlank()) {
            return List.of();
        }
        Map<String, Object> body = Map.of("query", query, "summary", true, "count", count);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.bochaai.com/v1/web-search"))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new RuntimeException("bocha http " + response.statusCode());
        }
        JsonNode values = objectMapper.readTree(response.body()).path("data").path("webPages").path("value");
        List<WebResult> out = new ArrayList<>();
        if (values.isArray()) {
            for (JsonNode v : values) {
                String snippet = v.path("snippet").asText("");
                if (snippet.isBlank()) {
                    snippet = v.path("summary").asText("");
                }
                out.add(new WebResult(v.path("name").asText(""), v.path("url").asText(""), snippet));
            }
        }
        return out;
    }

    /**
     * Tavily 搜索。
     */
    private List<WebResult> tavily(String query, int count) throws Exception {
        String key = extractApiKey(sysAiGlobalSettingService.getValue("search", "tavily").orElse(null));
        if (key == null || key.isBlank()) {
            return List.of();
        }
        Map<String, Object> body = Map.of("query", query, "max_results", count, "api_key", key);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.tavily.com/search"))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new RuntimeException("tavily http " + response.statusCode());
        }
        JsonNode results = objectMapper.readTree(response.body()).path("results");
        List<WebResult> out = new ArrayList<>();
        if (results.isArray()) {
            for (JsonNode v : results) {
                out.add(new WebResult(v.path("title").asText(""), v.path("url").asText(""), v.path("content").asText("")));
            }
        }
        return out;
    }

    /**
     * Bing 网页结果兜底。
     */
    private List<WebResult> bing(String query, int count) throws Exception {
        String url = "https://cn.bing.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&setlang=zh-CN&count=" + Math.max(count, 10);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", UA)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new RuntimeException("bing http " + response.statusCode());
        }
        String html = response.body();

        List<String> urls = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        Matcher h2 = H2_A.matcher(html);
        while (h2.find()) {
            urls.add(h2.group(1));
            titles.add(clean(h2.group(2)));
        }

        List<String> snippets = new ArrayList<>();
        Matcher clamp = LINE_CLAMP.matcher(html);
        while (clamp.find()) {
            snippets.add(clean(clamp.group(1)));
        }
        if (snippets.size() < titles.size()) {
            Matcher anyP = ANY_P.matcher(html);
            while (anyP.find()) {
                snippets.add(clean(anyP.group(1)));
            }
        }

        List<WebResult> out = new ArrayList<>();
        int n = Math.min(Math.min(urls.size(), titles.size()), count);
        for (int i = 0; i < n; i++) {
            String snip = i < snippets.size() ? snippets.get(i) : "";
            out.add(new WebResult(titles.get(i), urls.get(i), snip));
        }
        return out;
    }

    /**
     * 360 搜索网页结果兜底。
     *
     * <p>360 搜索（so.com）服务端渲染完整、对标准 HTTP 客户端友好，结果准确度高；
     * 真实目标地址存于链接的 {@code data-mdurl} 属性，翻页参数为 {@code pn}。
     * 逐页抓取并去重，累计到期望数量。</p>
     */
    private List<WebResult> so360(String query, int count) throws Exception {
        String enc = URLEncoder.encode(query, StandardCharsets.UTF_8);
        List<WebResult> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] pages = {
                "https://www.so.com/s?q=" + enc,
                "https://www.so.com/s?q=" + enc + "&pn=1",
                "https://www.so.com/s?q=" + enc + "&pn=2"
        };
        for (String pageUrl : pages) {
            if (out.size() >= count) {
                break;
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(pageUrl))
                    .header("User-Agent", UA)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                continue;
            }
            parse360Page(response.body(), out, seen, count);
        }
        return out;
    }

    /**
     * 解析一页 360 搜索结果，累计普通网页结果（跳过短视频、快资讯聚合与导航页）。
     */
    private void parse360Page(String html, List<WebResult> out, Set<String> seen, int count) {
        String[] blocks = html.split("<li[^>]*class=\"[^\"]*res-list");
        for (int i = 1; i < blocks.length && out.size() < count; i++) {
            String b = blocks[i];
            String u = firstGroup(b, "data-mdurl=\"(https?[^\"]+)\"");
            if (u == null || u.isBlank()) {
                continue;
            }
            if (u.contains("tv.360kan.com") || u.contains("360kuai.com") || u.contains("hao.360.com")) {
                continue;
            }
            if (!seen.add(u)) {
                continue;
            }
            String title = firstGroup(b, "<h3[^>]*>\\s*<a[^>]*>(.*?)</a>");
            if (title == null) {
                title = firstGroup(b, "<a[^>]*data-mdurl=\"[^\"]+\"[^>]*>(.*?)</a>");
            }
            title = clean(title == null ? "" : title);
            String desc = clean(firstGroup(b, "<p class=\"res-desc\">(.*?)</p>"));
            if (!title.isBlank()) {
                out.add(new WebResult(title, u, desc));
            }
        }
    }

    /**
     * 返回正则首次匹配的第一个分组，无匹配时返回 {@code null}。
     */
    private static String firstGroup(String input, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(input);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 从存储值提取 apiKey，兼容 {@code {apiKey=xxx}} 与 JSON 两种格式。
     */
    private static String extractApiKey(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = API_KEY.matcher(raw);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 去除 HTML 标签并还原常见实体。
     */
    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("<[^>]+>", "");
        t = t.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        return t.trim();
    }
}
