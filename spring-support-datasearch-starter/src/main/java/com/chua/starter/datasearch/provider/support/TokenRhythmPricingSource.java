package com.chua.starter.datasearch.provider.support;

import com.chua.starter.datasearch.provider.model.ProviderPricingRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

/**
 * 基元律动（TokenRhythm）模型目录实时抓取与解析。
 *
 * <p>控制台接口 {@code GET https://tokenrhythm.studio/api/models} 公开无鉴权，
 * 返回 {@code {code, message, data:[...]}} 信封；{@code data} 内每项是一个模型，
 * 价格字段（币种默认 CNY，按百万 token）：</p>
 * <ul>
 *   <li>{@code inputPrice} / {@code outputPrice} / {@code cacheReadPrice}：目录价；</li>
 *   <li>{@code effectiveInputPrice} / {@code effectiveOutputPrice} /
 *       {@code effectiveCacheReadPrice}：实付价（有限时折扣时低于目录价，
 *       无折扣时与目录价相同），取价优先用它；</li>
 *   <li>{@code billingMode}：只有 {@code per_1m_tokens} 按 token 计价，
 *       其余（按张 / 按秒 / 未定价）不出规则。</li>
 * </ul>
 *
 * <p>目录结构改版时解析会失败，调用方把失败原因原样返回，<b>不拿旧价覆盖</b>。</p>
 *
 * @author CH
 * @since 4.0.0.63
 */
public final class TokenRhythmPricingSource {

    /**
     * 平台标识（provider id / SPI name）
     */
    public static final String PLATFORM = "tokenrhythm";

    /**
     * 站点主机名（按上游地址匹配用）
     */
    public static final String HOST = "tokenrhythm.studio";

    /**
     * 模型目录地址（公开、无鉴权）
     */
    public static final String MODELS_URL = "https://tokenrhythm.studio/api/models";

    /**
     * 按百万 token 计价的 billingMode
     */
    public static final String BILLING_PER_1M_TOKENS = "per_1m_tokens";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 工具类，禁止实例化。
     */
    private TokenRhythmPricingSource() {
    }

    /**
     * 拉取模型目录（信封里的 data 数组）。
     *
     * @return 模型节点数组
     * @throws IOException          网络或解析失败
     * @throws InterruptedException 请求被中断
     */
    public static JsonNode fetchModels() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(MODELS_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        JsonNode data = MAPPER.readTree(response.body()).path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IOException("模型目录为空或结构变化");
        }
        return data;
    }

    /**
     * 目录 → 定价规则：每个按百万 token 计价的模型出
     * 输入（未命中缓存）/ 输出 / 输入（缓存命中）三条。
     *
     * @param models    模型节点数组（{@link #fetchModels()} 的返回值）
     * @param fetchedAt 抓取时间（ISO-8601，写入每条规则）
     * @return 规则列表（目录里没有按 token 计价的模型时为空）
     */
    public static List<ProviderPricingRule> toRules(JsonNode models, String fetchedAt) {
        List<ProviderPricingRule> rules = new ArrayList<>(16);
        for (JsonNode model : models) {
            if (!BILLING_PER_1M_TOKENS.equals(model.path("billingMode").asText(""))) {
                continue;
            }
            String id = model.path("id").asText("").trim();
            if (id.isEmpty()) {
                continue;
            }
            boolean discounted = model.path("hasDiscount").asBoolean(false);
            String currency = model.path("currency").asText("CNY");
            BigDecimal quantity = model.path("billingUnit").isNumber()
                    ? model.path("billingUnit").decimalValue() : BigDecimal.valueOf(1_000_000);
            addRule(rules, id, "input_cache_miss",
                    firstPrice(model, "effectiveInputPrice", "inputPrice"),
                    currency, quantity, discounted, fetchedAt);
            addRule(rules, id, "output",
                    firstPrice(model, "effectiveOutputPrice", "outputPrice"),
                    currency, quantity, discounted, fetchedAt);
            addRule(rules, id, "input_cache_hit",
                    firstPrice(model, "effectiveCacheReadPrice", "cacheReadPrice"),
                    currency, quantity, discounted, fetchedAt);
        }
        return rules;
    }

    /**
     * 组装一条规则并加入清单；价格缺失或不是数字时跳过（不猜值）。
     *
     * @param rules      结果清单
     * @param modelId    模型标识
     * @param item       计费项
     * @param price      单价文本
     * @param currency   币种
     * @param quantity   计价单位数
     * @param discounted 是否限时折扣价
     * @param fetchedAt  抓取时间
     */
    private static void addRule(List<ProviderPricingRule> rules, String modelId, String item,
                                String price, String currency, BigDecimal quantity,
                                boolean discounted, String fetchedAt) {
        if (price == null || price.isBlank()) {
            return;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(price.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return;
        }
        ProviderPricingRule rule = new ProviderPricingRule();
        rule.setProviderId(PLATFORM);
        rule.setModelPattern(modelId);
        rule.setItem(item);
        rule.setUnit("token");
        rule.setQuantity(quantity);
        rule.setStandardPrice(amount);
        rule.setCurrency(currency);
        rule.setVerified(true);
        rule.setSourceUrl(MODELS_URL);
        rule.setFetchedAt(fetchedAt);
        if (discounted) {
            rule.setNote("限时折扣实付价");
        }
        rules.add(rule);
    }

    /**
     * 取价：优先实付价（折扣后），缺失回落目录价。
     *
     * @param model     模型节点
     * @param preferred 实付价字段
     * @param fallback  目录价字段
     * @return 价格文本；两个都缺时为 null
     */
    private static String firstPrice(JsonNode model, String preferred, String fallback) {
        String price = priceText(model.path(preferred));
        if (price != null) {
            return price;
        }
        return priceText(model.path(fallback));
    }

    /**
     * 价格节点 → 文本（字符串或数字都收）。
     *
     * @param node 价格节点
     * @return 文本；缺失 / null / 空串时为 null
     */
    private static String priceText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asText();
        }
        if (node.isTextual()) {
            String text = node.asText("").trim();
            return text.isEmpty() ? null : text;
        }
        return null;
    }

    /**
     * 当前时间（ISO-8601），供调用方做 fetchedAt。
     *
     * @return ISO-8601 时间串
     */
    public static String now() {
        return Instant.now().toString();
    }
}
