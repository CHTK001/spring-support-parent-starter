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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agnes 官方价格页实时抓取与解析。
 *
 * <p>价格页结构改版时解析会失败，此时返回 {@link ProviderPricingRefresh#fail(String)}，
 * 同步接口把失败原因原样返回，<b>不拿旧价覆盖数据库</b>。</p>
 *
 * <p>当前解析项（与 <a href="https://agnes-ai.com/en/docs/pricing">官方 Model Pricing 页</a>
 * 的表格一一对应，一行一计费项）：</p>
 * <ul>
 *   <li><b>文本模型</b>：cached input / input / output，每百万 token；</li>
 *   <li><b>图片模型</b>：1K / 2K / 3K / 4K 输出图像按千张 + 第 4 张起参考图按张；</li>
 *   <li><b>视频模型</b>：720P / 1080P / 1K / 2K 输出视频按秒 + 第 6 张起输入图片按张。</li>
 * </ul>
 *
 * <p>金额口径：页面同时列「List price（刊例价）」与「Angel User Trial Price（当前体验价）」，
 * {@link ProviderPricingRule#getStandardPrice()} 取当前实际计费的体验价，刊例价写进 note，
 * 与 DeepSeek TTS 的「当前价 / 原价」写法一致。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
public final class AgnesPricingSource {

    /**
     * 官方价格页
     */
    public static final String URL = "https://agnes-ai.com/en/docs/pricing";

    /**
     * 文本模型一行：模型名 [Coming soon] Cached input 刊例/体验 Input 刊例/体验 Output 刊例/体验（每百万 token）
     */
    private static final Pattern TEXT_ROW = Pattern.compile(
            "(agnes-[a-z0-9.\\-]+)\\s+(Coming soon)?\\s*Cached input\\s+\\$([0-9.]+)\\s*/\\s*M\\s+\\$([0-9.]+)\\s*/\\s*M"
                    + "\\s+Input tokens\\s+\\$([0-9.]+)\\s*/\\s*M\\s+\\$([0-9.]+)\\s*/\\s*M"
                    + "\\s+Output tokens\\s+\\$([0-9.]+)\\s*/\\s*M\\s+\\$([0-9.]+)\\s*/\\s*M");

    /**
     * 图片模型一行：四个分辨率档（每千张）+ 第 4 张起参考图（每张）
     */
    private static final Pattern IMAGE_ROW = Pattern.compile(
            "(agnes-image-[a-z0-9.\\-]+)\\s+"
                    + "1K output image\\s+\\$([0-9.]+)\\s*/\\s*1,000 images\\s+\\$([0-9.]+)\\s+"
                    + "2K output image\\s+\\$([0-9.]+)\\s*/\\s*1,000 images\\s+\\$([0-9.]+)\\s+"
                    + "3K output image\\s+\\$([0-9.]+)\\s*/\\s*1,000 images\\s+\\$([0-9.]+)\\s+"
                    + "4K output image\\s+\\$([0-9.]+)\\s*/\\s*1,000 images\\s+\\$([0-9.]+)\\s+"
                    + "Input reference images from the 4th onward\\s+\\$([0-9.]+)\\s*/\\s*image\\s+\\$([0-9.]+)");

    /**
     * 视频模型 agnes-video-2.5 一行：四个分辨率档（每秒）+ 第 6 张起输入图片（每张）
     */
    private static final Pattern VIDEO_ROW = Pattern.compile(
            "(agnes-video-2\\.5)\\s+"
                    + "720P output video\\s+\\$([0-9.]+)\\s*/\\s*second\\s+\\$([0-9.]+)\\s*/\\s*second\\s+"
                    + "1080P output video\\s+\\$([0-9.]+)\\s*/\\s*second\\s+\\$([0-9.]+)\\s*/\\s*second\\s+"
                    + "1K output video\\s+\\$([0-9.]+)\\s*/\\s*second\\s+\\$([0-9.]+)\\s*/\\s*second\\s+"
                    + "2K output video\\s+\\$([0-9.]+)\\s*/\\s*second\\s+\\$([0-9.]+)\\s*/\\s*second\\s+"
                    + "Input images from the 6th onward\\s+\\$([0-9.]+)\\s*/\\s*image\\s+\\$([0-9.]+)\\s*/\\s*image");

    /**
     * 视频模型 agnes-video-2.5-flash 一行：仅 720P 档，计费项文案为「720P video duration」
     */
    private static final Pattern VIDEO_FLASH_ROW = Pattern.compile(
            "(agnes-video-2\\.5-flash)\\s+720P video duration"
                    + "\\s+\\$([0-9.]+)\\s*/\\s*second\\s+\\$([0-9.]+)\\s*/\\s*second");

    /**
     * 工具类私有构造器，禁止实例化。
     */
    private AgnesPricingSource() {
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
        List<ProviderPricingRule> rules = new ArrayList<>(32);
        addTextRules(rules, text, sourceUrl, fetchedAt);
        addImageRules(rules, text, sourceUrl, fetchedAt);
        addVideoRules(rules, text, sourceUrl, fetchedAt);
        if (rules.isEmpty()) {
            return ProviderPricingRefresh.fail("未解析到价格（页面结构可能已改版）");
        }
        return ProviderPricingRefresh.ok(rules, fetchedAt);
    }

    /**
     * 文本模型：cached input / input / output 三个计费项，每百万 token。
     *
     * @param rules     接收规则的目标列表
     * @param text      页面纯文本
     * @param sourceUrl 来源地址
     * @param fetchedAt 抓取时间
     */
    private static void addTextRules(List<ProviderPricingRule> rules, String text,
                                     String sourceUrl, String fetchedAt) {
        Matcher matcher = TEXT_ROW.matcher(text);
        while (matcher.find()) {
            String model = matcher.group(1);
            boolean comingSoon = matcher.group(2) != null;
            String suffix = comingSoon ? "；模型 Coming soon" : "";
            addTokenRule(rules, model, "input_cache_hit", matcher.group(3), matcher.group(4), suffix, sourceUrl, fetchedAt);
            addTokenRule(rules, model, "input_cache_miss", matcher.group(5), matcher.group(6), suffix, sourceUrl, fetchedAt);
            addTokenRule(rules, model, "output", matcher.group(7), matcher.group(8), suffix, sourceUrl, fetchedAt);
        }
    }

    /**
     * 追加一条每百万 token 的文本模型规则。
     *
     * @param rules     接收规则的目标列表
     * @param model     模型名
     * @param item      计费项
     * @param list      刊例价（List price）
     * @param trial     体验价（Angel User Trial Price，当前实际计费价）
     * @param suffix    备注后缀（如 Coming soon）
     * @param sourceUrl 来源地址
     * @param fetchedAt 抓取时间
     */
    private static void addTokenRule(List<ProviderPricingRule> rules, String model, String item,
                                     String list, String trial, String suffix,
                                     String sourceUrl, String fetchedAt) {
        String note = "Agnes 官方定价页（每百万 token，USD）：当前价 $" + trial
                + "，刊例价 $" + list + suffix;
        rules.add(rule(model, item, "token", new BigDecimal("1000000"),
                money(trial), note, sourceUrl, fetchedAt));
    }

    /**
     * 图片模型：1K / 2K / 3K / 4K 输出图像（每千张）+ 第 4 张起参考图（每张）。
     *
     * @param rules     接收规则的目标列表
     * @param text      页面纯文本
     * @param sourceUrl 来源地址
     * @param fetchedAt 抓取时间
     */
    private static void addImageRules(List<ProviderPricingRule> rules, String text,
                                      String sourceUrl, String fetchedAt) {
        Matcher matcher = IMAGE_ROW.matcher(text);
        while (matcher.find()) {
            String model = matcher.group(1);
            String[] tiers = {"1K", "2K", "3K", "4K"};
            for (int i = 0; i < tiers.length; i++) {
                String list = matcher.group(2 + i * 2);
                String trial = matcher.group(3 + i * 2);
                String note = "Agnes 官方定价页（USD）：输出图像 " + tiers[i] + " 档，当前价 $" + trial
                        + " / 千张，刊例价 $" + list + " / 千张";
                rules.add(rule(model, "image", "image", new BigDecimal("1000"),
                        money(trial), note, sourceUrl, fetchedAt));
            }
            String refNote = "Agnes 官方定价页（USD）：输入参考图第 4 张起按张计费，当前价 $"
                    + matcher.group(11) + " / 张，刊例价 $" + matcher.group(10) + " / 张";
            rules.add(rule(model, "image", "image", BigDecimal.ONE,
                    money(matcher.group(11)), refNote, sourceUrl, fetchedAt));
        }
    }

    /**
     * 视频模型：agnes-video-2.5 四档按秒 + 第 6 张起输入图片；agnes-video-2.5-flash 仅 720P 档。
     *
     * @param rules     接收规则的目标列表
     * @param text      页面纯文本
     * @param sourceUrl 来源地址
     * @param fetchedAt 抓取时间
     */
    private static void addVideoRules(List<ProviderPricingRule> rules, String text,
                                      String sourceUrl, String fetchedAt) {
        Matcher matcher = VIDEO_ROW.matcher(text);
        if (matcher.find()) {
            String model = matcher.group(1);
            String[] tiers = {"720P", "1080P", "1K", "2K"};
            for (int i = 0; i < tiers.length; i++) {
                String list = matcher.group(2 + i * 2);
                String trial = matcher.group(3 + i * 2);
                String note = "Agnes 官方定价页（USD）：输出视频 " + tiers[i] + " 档，当前价 $" + trial
                        + " / 秒，刊例价 $" + list + " / 秒";
                rules.add(rule(model, "video", "second", BigDecimal.ONE,
                        money(trial), note, sourceUrl, fetchedAt));
            }
            String imgNote = "Agnes 官方定价页（USD）：输入图片第 6 张起按张计费，当前价 $"
                    + matcher.group(11) + " / 张，刊例价 $" + matcher.group(10) + " / 张";
            rules.add(rule(model, "image", "image", BigDecimal.ONE,
                    money(matcher.group(11)), imgNote, sourceUrl, fetchedAt));
        }
        Matcher flash = VIDEO_FLASH_ROW.matcher(text);
        if (flash.find()) {
            String note = "Agnes 官方定价页（USD）：输出视频 720P 档，当前价 $" + flash.group(3)
                    + " / 秒，刊例价 $" + flash.group(2) + " / 秒";
            rules.add(rule(flash.group(1), "video", "second", BigDecimal.ONE,
                    money(flash.group(3)), note, sourceUrl, fetchedAt));
        }
    }

    /**
     * 构建一条规则（无峰谷，只填 standardPrice）。
     *
     * @param model         模型名
     * @param item          计费项
     * @param unit          计费单位
     * @param quantity      每 N 个单位计价
     * @param standardPrice 当前实际计费价（Angel User Trial Price）
     * @param note          备注（含刊例价）
     * @param sourceUrl     来源地址
     * @param fetchedAt     抓取时间
     * @return 构建完成的规则
     */
    private static ProviderPricingRule rule(String model, String item, String unit, BigDecimal quantity,
                                            BigDecimal standardPrice, String note, String sourceUrl,
                                            String fetchedAt) {
        ProviderPricingRule rule = new ProviderPricingRule();
        rule.setProviderId("agnes");
        rule.setModelPattern(model);
        rule.setItem(item);
        rule.setUnit(unit);
        rule.setQuantity(quantity);
        rule.setStandardPrice(standardPrice);
        rule.setCurrency("USD");
        rule.setNote(note);
        rule.setSourceUrl(sourceUrl);
        rule.setVerified(true);
        rule.setFetchedAt(fetchedAt);
        return rule;
    }

    /**
     * 金额。
     *
     * @param value 金额文本（如 "0.05"）
     * @return 解析后的金额
     */
    private static BigDecimal money(String value) {
        return new BigDecimal(value.replace(",", ""));
    }

    /**
     * HTML → 纯文本。
     *
     * @param html 原始 HTML
     * @return 归一化后的纯文本
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
