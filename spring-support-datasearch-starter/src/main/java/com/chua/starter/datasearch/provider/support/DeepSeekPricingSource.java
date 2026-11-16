package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.PriceWindow;
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
 * DeepSeek 官方价格页实时抓取与解析。
 *
 * <p>价格页结构改版时解析会失败，此时返回 {@link ProviderPricingRefresh#fail(String)}，
 * 同步接口把失败原因原样返回，<b>不拿静态登记表里的旧价覆盖数据库</b>。静态登记表只用于
 * 页面展示与离线兜底。</p>
 *
 * <p>当前解析项：模型 token 峰谷价（cache hit / cache miss / output）、搜索 / 抓取 /
 * 文件解析按千次、TTS 按万字符、ASR 按小时。金额单位按页面原文（USD）。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
public final class DeepSeekPricingSource {

    /**
     * 官方价格页
     */
    public static final String URL = "https://api-docs.deepseek.com/quick_start/pricing";

    private static final Pattern MODEL = Pattern.compile("(?i)MODEL\\s+(.+?)\\s+BASE URL");
    private static final Pattern MODEL_ID = Pattern.compile("(deepseek-[a-z0-9.\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONEY = Pattern.compile("\\$\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern PER_UNIT = Pattern.compile(
            "Per\\s+([0-9,]+)\\s+(searches|fetches|pages)\\s+\\$\\s*([0-9]+(?:\\.[0-9]+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TTS = Pattern.compile(
            "Text to Speech\\s*\\(TTS\\)\\s+Per\\s+([0-9,]+)\\s+characters\\s+\\$\\s*([0-9]+(?:\\.[0-9]+)?)"
                    + "\\s+\\$\\s*([0-9]+(?:\\.[0-9]+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ASR = Pattern.compile(
            "Speech to Text\\s*\\(ASR\\)\\s+Per\\s+hour of audio\\s+\\$\\s*([0-9]+(?:\\.[0-9]+)?)"
                    + "\\s+\\$\\s*([0-9]+(?:\\.[0-9]+)?)",
            Pattern.CASE_INSENSITIVE);

    private DeepSeekPricingSource() {
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
     * 解析 HTML（独立出来便于用固定样本回归，不依赖网络）。
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
        String text = text(html);
        List<String> models = models(text);
        if (models.isEmpty()) {
            return ProviderPricingRefresh.fail("未解析到模型名（页面结构可能已改版）");
        }
        List<PriceWindow> windows = windows(text);
        String policy = policy(text);
        boolean peak = windows.size() > 0;

        List<ProviderPricingRule> rules = new ArrayList<>(16);
        addTokenRules(rules, models, text, "1M INPUT TOKENS (CACHE HIT)",
                "1M INPUT TOKENS (CACHE MISS)", "input_cache_hit", windows, policy, peak, sourceUrl, fetchedAt);
        addTokenRules(rules, models, text, "1M INPUT TOKENS (CACHE MISS)",
                "1M OUTPUT TOKENS", "input_cache_miss", windows, policy, peak, sourceUrl, fetchedAt);
        addTokenRules(rules, models, text, "1M OUTPUT TOKENS",
                "Concurrency Limit", "output", windows, policy, peak, sourceUrl, fetchedAt);

        Matcher per = PER_UNIT.matcher(text);
        while (per.find()) {
            String unit = per.group(2).toLowerCase(Locale.ROOT);
            String item = "searches".equals(unit) ? "search" : "fetches".equals(unit) ? "fetch" : "file_parse";
            String single = "searches".equals(unit) ? "search" : "fetches".equals(unit) ? "fetch" : "page";
            rules.add(rule("*", item, single, quantity(per.group(1)), null, null,
                    money(per.group(3)), windows, policy, "官方页面按次计价", sourceUrl, fetchedAt, peak));
        }
        Matcher tts = TTS.matcher(text);
        if (tts.find()) {
            BigDecimal list = money(tts.group(2));
            BigDecimal current = money(tts.group(3));
            rules.add(rule("*", "tts", "character", quantity(tts.group(1)), null, null,
                    current, null, null, "官方页面：当前价 $" + current.toPlainString()
                            + "，原价 $" + list.toPlainString(), sourceUrl, fetchedAt, false));
        }
        Matcher asr = ASR.matcher(text);
        if (asr.find()) {
            BigDecimal list = money(asr.group(1));
            BigDecimal current = money(asr.group(2));
            rules.add(rule("*", "asr", "hour", BigDecimal.ONE, null, null,
                    current, null, null, "官方页面：当前价 $" + current.toPlainString()
                            + "，原价 $" + list.toPlainString(), sourceUrl, fetchedAt, false));
        }
        if (rules.isEmpty()) {
            return ProviderPricingRefresh.fail("未解析到价格（页面结构可能已改版）");
        }
        return ProviderPricingRefresh.ok(rules, fetchedAt);
    }

    /**
     * 解析模型 token 的峰谷价。
     */
    private static void addTokenRules(List<ProviderPricingRule> rules, List<String> models, String text,
                                      String start, String end, String item, List<PriceWindow> windows,
                                      String policy, boolean peak, String sourceUrl, String fetchedAt) {
        String segment = between(text, start, end);
        if (segment == null) {
            return;
        }
        int peakAt = -1;
        Matcher peakMatcher = Pattern.compile("(?<!-)\\bPEAK\\b").matcher(segment);
        if (peakMatcher.find()) {
            peakAt = peakMatcher.start();
        }
        if (peakAt < 0) {
            return;
        }
        List<BigDecimal> off = moneyList(segment.substring(0, peakAt));
        List<BigDecimal> high = moneyList(segment.substring(peakAt + 4));
        for (int i = 0; i < models.size(); i++) {
            BigDecimal offPrice = i < off.size() ? off.get(i) : null;
            BigDecimal peakPrice = i < high.size() ? high.get(i) : null;
            if (peakPrice == null && offPrice == null) {
                continue;
            }
            rules.add(rule(models.get(i), item, "token", new BigDecimal("1000000"),
                    peakPrice, offPrice, null, windows, policy, "官方价格页（每百万 token）",
                    sourceUrl, fetchedAt, peak));
        }
    }

    /**
     * 构建一条规则。
     */
    private static ProviderPricingRule rule(String model, String item, String unit, BigDecimal quantity,
                                            BigDecimal peakPrice, BigDecimal offPeakPrice,
                                            BigDecimal standardPrice, List<PriceWindow> windows,
                                            String policy, String note, String sourceUrl,
                                            String fetchedAt, boolean peak) {
        ProviderPricingRule rule = new ProviderPricingRule();
        rule.setProviderId("deepseek");
        rule.setModelPattern(model);
        rule.setItem(item);
        rule.setUnit(unit);
        rule.setQuantity(quantity);
        rule.setPeakPrice(peakPrice);
        rule.setOffPeakPrice(offPeakPrice);
        rule.setStandardPrice(standardPrice);
        rule.setCurrency("USD");
        if (peak) {
            rule.setPeakWindows(windows);
            rule.setOffPeakPolicy(policy);
        }
        rule.setNote(note);
        rule.setSourceUrl(sourceUrl);
        rule.setVerified(true);
        rule.setFetchedAt(fetchedAt);
        return rule;
    }

    /**
     * 模型名。
     */
    private static List<String> models(String text) {
        List<String> ids = new ArrayList<>(2);
        Matcher matcher = MODEL.matcher(text);
        if (!matcher.find()) {
            return ids;
        }
        Matcher id = MODEL_ID.matcher(matcher.group(1));
        while (id.find()) {
            String value = id.group(1).toLowerCase(Locale.ROOT);
            if (!ids.contains(value)) {
                ids.add(value);
            }
        }
        return ids;
    }

    /**
     * 高峰时段：UTC 周一至周五，页面上写了几段就解析几段。
     */
    private static List<PriceWindow> windows(String text) {
        List<PriceWindow> windows = new ArrayList<>(2);
        String segment = between(text, "Peak hours are", "All other hours");
        if (segment == null) {
            return windows;
        }
        Matcher time = Pattern.compile("([0-2]\\d:[0-5]\\d)\\s*-\\s*([0-2]\\d:[0-5]\\d)").matcher(segment);
        boolean mondayToFriday = segment.toLowerCase(Locale.ROOT).contains("monday through friday");
        while (time.find()) {
            PriceWindow window = new PriceWindow();
            window.setTimezone("UTC");
            window.setWeekdays(mondayToFriday ? List.of(1, 2, 3, 4, 5) : List.of());
            window.setStartMinute(minutes(time.group(1)));
            window.setEndMinute(minutes(time.group(2)));
            windows.add(window);
        }
        return windows;
    }

    /**
     * 闲时说明。
     */
    private static String policy(String text) {
        Matcher matcher = Pattern.compile("Off-peak rates[^.]*\\.(?:[^.]*\\.)?").matcher(text);
        return matcher.find() ? matcher.group().trim() : null;
    }

    /**
     * 取两个标记之间的文本。
     */
    private static String between(String text, String start, String end) {
        int from = text.indexOf(start);
        if (from < 0) {
            return null;
        }
        int to = text.indexOf(end, from + start.length());
        return to < 0 ? text.substring(from + start.length()) : text.substring(from + start.length(), to);
    }

    /**
     * 取金额列表。
     */
    private static List<BigDecimal> moneyList(String text) {
        List<BigDecimal> list = new ArrayList<>(2);
        Matcher matcher = MONEY.matcher(text);
        while (matcher.find()) {
            list.add(money(matcher.group(1)));
        }
        return list;
    }

    /**
     * 金额。
     */
    private static BigDecimal money(String value) {
        return new BigDecimal(value.replace(",", ""));
    }

    /**
     * 数量。
     */
    private static BigDecimal quantity(String value) {
        return new BigDecimal(value.replace(",", ""));
    }

    /**
     * HH:mm → 当天分钟数。
     */
    private static int minutes(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    /**
     * HTML → 纯文本。
     */
    private static String text(String html) {
        return html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
