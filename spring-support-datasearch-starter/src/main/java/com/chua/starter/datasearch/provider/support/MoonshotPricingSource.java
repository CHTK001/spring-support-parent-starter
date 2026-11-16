package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.ProviderPricingRefresh;
import com.chua.starter.datasearch.provider.model.ProviderPricingRule;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Moonshot（Kimi API）官方价格页实时抓取与解析。
 *
 * <p>价格页是 Mintlify 文档，表格数据在页面内嵌的 DocTable {@code rows} 里，
 * 不在最终 HTML 的可见表格中；因此按 {@code rows:[[...]]} 解析，而不是按 DOM 文本解析。
 * 当前支持 K3 的五列（输入 / 输出 / 缓存命中 / 缓存写入 5min / 缓存写入 1h）与
 * K2 的三列（输入 / 输出 / 缓存命中），单位均为人民币 / 百万 token。</p>
 *
 * <p>页面结构改版时返回失败，<b>不拿静态登记表旧价覆盖数据库</b>。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
public final class MoonshotPricingSource {

    /**
     * 官方价格页（platform.moonshot.cn 会 302 到 platform.kimi.com，HttpClient 自动跟随）
     */
    public static final String URL = "https://platform.moonshot.cn/docs/pricing/chat";

    private static final Pattern ROWS = Pattern.compile("rows:\\[\\[(.*?)]]", Pattern.DOTALL);
    private static final Pattern MODEL = Pattern.compile("`([a-z0-9][a-z0-9.\\-]*)`",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE = Pattern.compile(
            "¥\\s*([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*MTok", Pattern.CASE_INSENSITIVE);

    private MoonshotPricingSource() {
    }

    /**
     * 抓取并解析官方价格页。
     *
     * @param url 价格页地址
     * @return 刷新结果；失败时 error 有值、rules 为空
     */
    public static ProviderPricingRefresh fetch(String url) {
        String target = url == null || url.isBlank() ? URL : url;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 (compatible; SCNet-provider-pricing/1.0)")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return ProviderPricingRefresh.fail("官方价格页 HTTP " + response.statusCode());
            }
            return parse(response.body(), target, Instant.now().toString());
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ProviderPricingRefresh.fail("官方价格页抓取失败: " + e.getMessage());
        }
    }

    /**
     * 解析价格页（独立出来便于用固定样本回归，不依赖网络）。
     *
     * @param html      HTML
     * @param sourceUrl 来源地址
     * @param fetchedAt 抓取时间
     * @return 刷新结果
     */
    public static ProviderPricingRefresh parse(String html, String sourceUrl, String fetchedAt) {
        if (html == null || html.isBlank()) {
            return ProviderPricingRefresh.fail("官方价格页内容为空");
        }
        List<ProviderPricingRule> rules = new ArrayList<>(16);
        Matcher rows = ROWS.matcher(html);
        while (rows.find()) {
            String body = rows.group(1);
            String[] cells = body.split("],\\[");
            for (String cell : cells) {
                addRow(rules, cell, sourceUrl, fetchedAt);
            }
        }
        if (rules.isEmpty()) {
            return ProviderPricingRefresh.fail("未解析到价格表（页面结构可能已改版）");
        }
        return ProviderPricingRefresh.ok(rules, fetchedAt);
    }

    /**
     * 解析表格的一行。
     */
    private static void addRow(List<ProviderPricingRule> rules, String row, String sourceUrl,
                               String fetchedAt) {
        List<BigDecimal> prices = new ArrayList<>(5);
        Matcher price = PRICE.matcher(row);
        while (price.find()) {
            prices.add(new BigDecimal(price.group(1)));
        }
        if (prices.size() < 3) {
            return;
        }
        String model = model(row);
        if (model == null) {
            return;
        }
        // 三列：输入 / 输出 / 缓存命中；五列再加缓存写入 5min / 1h
        String[] items = prices.size() >= 5
                ? new String[]{"input", "output", "input_cache_hit", "input_cache_write_5m", "input_cache_write_1h"}
                : new String[]{"input", "output", "input_cache_hit"};
        for (int i = 0; i < items.length && i < prices.size(); i++) {
            ProviderPricingRule rule = new ProviderPricingRule();
            rule.setProviderId("moonshot");
            rule.setModelPattern(model);
            rule.setItem(items[i]);
            rule.setUnit("token");
            rule.setQuantity(new BigDecimal("1000000"));
            rule.setStandardPrice(prices.get(i));
            rule.setCurrency("CNY");
            rule.setNote("官方价格页（人民币 / 百万 token）");
            rule.setSourceUrl(sourceUrl);
            rule.setVerified(true);
            rule.setFetchedAt(fetchedAt);
            rules.add(rule);
        }
    }

    /**
     * 从单元格里取模型名：K3 行是 {@code children:`name`}，K2 行是首个反引号字符串。
     */
    private static String model(String row) {
        Matcher matcher = MODEL.matcher(row);
        while (matcher.find()) {
            String value = matcher.group(1).toLowerCase(Locale.ROOT);
            if (value.startsWith("kimi-")) {
                return value;
            }
        }
        return null;
    }
}
