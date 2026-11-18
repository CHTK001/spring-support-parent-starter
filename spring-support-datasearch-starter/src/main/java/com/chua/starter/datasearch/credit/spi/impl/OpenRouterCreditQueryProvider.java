package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenRouter 余额实现。
 *
 * <p>主口径走官方账户余额接口：{@code GET https://openrouter.ai/api/v1/credits}
 * （Bearer 鉴权），返回 {@code data.total_credits}（累计充值）与
 * {@code data.total_usage}（累计消费），剩余 = 充值 − 消费。该接口只认
 * <b>管理 Key</b>（management key）：普通推理 Key 调它返回 403
 * {@code "Only management keys can perform this operation"}。</p>
 *
 * <p>普通推理 Key 的兜底口径：{@code GET https://openrouter.ai/api/v1/key}
 * （任意 Key 可调），返回本 Key 的 {@code data.usage}（累计消费）与可选的
 * {@code data.limit_remaining}（仅给 Key 配了 per-key 上限时才有）。
 * 两段都有时按「剩余 / 已用」两段读数返回；只有用量时按「查到数据但没有主余额」
 * 口径返回（parts 空），读数放 {@code extra.key_usage}，前端按
 * {@code mode=key-usage} 单独展示「本 Key 累计已用」，不把消费当余额。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Spi("openrouter")
public class OpenRouterCreditQueryProvider implements CreditQueryProvider {

    /**
     * 官方账户余额接口路径（需管理 Key）
     */
    private static final String CREDITS_PATH = "/api/v1/credits";

    /**
     * 本 Key 信息接口路径（任意 Key 可调，普通推理 Key 的兜底口径）
     */
    private static final String KEY_PATH = "/api/v1/key";

    /**
     * 官方 API 根地址：供应商行未配上游地址时使用
     */
    private static final String DEFAULT_BASE = "https://openrouter.ai";

    /**
     * 币种：OpenRouter 余额与用量均按美元计
     */
    private static final String CURRENCY = "USD";

    /**
     * 响应映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "openrouter";
    }

    /**
     * 查余额：先走官方账户余额接口（管理 Key），失败（含普通 Key 的 403）时
     * 回落本 Key 用量接口。
     *
     * @param query 请求
     * @return 结果；两个口径都拿不到数时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        CreditResult primary = queryByCredits(query);
        if (primary != null && primary.isOk()) {
            return primary;
        }
        CreditResult fallback = queryByApiKey(query);
        if (fallback != null && fallback.isOk()) {
            return fallback;
        }
        String primaryError = primary == null ? "未执行" : primary.getError();
        String fallbackError = fallback == null ? "未执行" : fallback.getError();
        return CreditResult.fail(name(),
                "账户余额接口（需管理 Key）：" + primaryError + "；本 Key 用量接口：" + fallbackError);
    }

    /**
     * 官方账户余额接口：GET {base}/api/v1/credits，Bearer 鉴权（需管理 Key），
     * 返回累计充值 / 累计消费，剩余 = 充值 − 消费。
     *
     * @param query 请求
     * @return 结果；接口失败（含普通 Key 的 403）或响应不可解析时 error 有值
     */
    private CreditResult queryByCredits(CreditQuery query) {
        try {
            String url = CreditHttp.join(apiBase(query), CREDITS_PATH);
            String body = CreditHttp.fetch(request(query, url), url);
            JsonNode data = MAPPER.readTree(body).path("data");
            JsonNode creditsNode = data.path("total_credits");
            JsonNode usageNode = data.path("total_usage");
            if (!creditsNode.isNumber() || !usageNode.isNumber()) {
                return CreditResult.fail(name(), "账户余额接口未返回 total_credits / total_usage");
            }
            BigDecimal credits = creditsNode.decimalValue();
            BigDecimal usage = usageNode.decimalValue();
            BigDecimal remain = credits.subtract(usage);
            List<BalancePart> parts = new ArrayList<>(2);
            parts.add(part("剩余", remain.signum() < 0 ? BigDecimal.ZERO : remain));
            parts.add(part("已用", usage));
            return CreditResult.ok(name(), parts, body);
        } catch (RuntimeException | IOException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 本 Key 用量接口：GET {base}/api/v1/key，任意 Key 可调，普通推理 Key 的兜底口径。
     *
     * <p>返回本 Key 累计消费（{@code data.usage}）与可选的本 Key 剩余
     * （{@code data.limit_remaining}，仅配了 per-key 上限时才有）：两段都有时按
     * 「剩余 / 已用」返回；只有用量时按「查到数据但没有主余额」返回（parts 空），
     * 读数放 extra，避免聚合层把消费当余额摆进度条。</p>
     *
     * @param query 请求
     * @return 结果；接口失败或响应不可解析时 error 有值
     */
    private CreditResult queryByApiKey(CreditQuery query) {
        try {
            String url = CreditHttp.join(apiBase(query), KEY_PATH);
            String body = CreditHttp.fetch(request(query, url), url);
            JsonNode data = MAPPER.readTree(body).path("data");
            JsonNode usageNode = data.path("usage");
            JsonNode remainNode = data.path("limit_remaining");
            BigDecimal usage = usageNode.isNumber() ? usageNode.decimalValue() : null;
            BigDecimal remain = remainNode.isNumber() ? remainNode.decimalValue() : null;
            if (usage == null && remain == null) {
                return CreditResult.fail(name(), "Key 用量接口未返回 usage / limit_remaining");
            }
            if (usage != null && remain != null) {
                List<BalancePart> parts = new ArrayList<>(2);
                parts.add(part("本 Key 剩余", remain));
                parts.add(part("本 Key 已用", usage));
                return CreditResult.ok(name(), parts, body);
            }
            // 只有单边读数（当前普通推理 Key 实测只有累计消费、没有 per-key 上限）：
            // 按「查到数据但没有主余额」口径返回（parts 空），读数放 extra，
            // 前端按 mode=key-usage 单独展示「本 Key 累计已用」
            CreditResult result = CreditResult.okData(name(), body);
            Map<String, Object> extra = new LinkedHashMap<>(4);
            extra.put("mode", "key-usage");
            if (usage != null) {
                extra.put("key_usage", usage);
            }
            if (remain != null) {
                extra.put("key_limit_remaining", remain);
            }
            result.setExtra(extra);
            return result;
        } catch (RuntimeException | IOException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 推断 OpenRouter API 根地址：供应商行的上游地址可能是
     * {@code https://openrouter.ai/api/v1}、{@code .../v1} 或根地址，都归一到根。
     *
     * @param query 请求
     * @return API 根地址
     */
    private static String apiBase(CreditQuery query) {
        String url = query.getUrl();
        if (url == null || url.isBlank()) {
            return DEFAULT_BASE;
        }
        String text = url.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith("/api/v1")) {
            text = text.substring(0, text.length() - "/api/v1".length());
        } else if (text.endsWith("/v1")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.isBlank() ? DEFAULT_BASE : text;
    }

    /**
     * 组装余额请求：GET + Bearer 令牌（CreditHttp 按 Authorization 头默认补 Bearer），
     * 自定义请求头原样带过去。
     *
     * @param query 原始请求
     * @param url   完整地址
     * @return 余额请求
     */
    private static CreditQuery request(CreditQuery query, String url) {
        CreditQuery request = new CreditQuery();
        request.setKind(query.getKind());
        request.setProviderId(query.getProviderId());
        request.setUrl(url);
        request.setOriginUrl(query.getOriginUrl());
        request.setMethod("GET");
        request.setToken(query.getToken());
        request.setTokenHeader(query.getTokenHeader());
        if (query.getHeaders() != null) {
            for (Map.Entry<String, String> entry : query.getHeaders().entrySet()) {
                request.header(entry.getKey(), entry.getValue());
            }
        }
        return request;
    }

    /**
     * 组装一段读数（美元）。
     *
     * @param label  段名
     * @param amount 金额
     * @return 读数
     */
    private static BalancePart part(String label, BigDecimal amount) {
        BalancePart part = new BalancePart();
        part.setLabel(label);
        part.setAmount(amount);
        part.setCurrency(CURRENCY);
        return part;
    }
}
