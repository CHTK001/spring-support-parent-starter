package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.network.client.ClientResponse;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditHttp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 100milionseek 余额实现。
 *
 * <p>该平台没有独立余额端点（{@code /v1/dashboard/billing/*} 404，控制台接口
 * {@code /api/user/self} / {@code /api/me} 需要网页会话登录，API Key 过不去），
 * 但 OpenAI 兼容的模型列表接口 {@code GET {base}/v1/models}（Bearer Key 鉴权）
 * 会在响应头返回按账号的每日配额（2026-11 实测）：</p>
 * <ul>
 *   <li>{@code x-quota-limit}：当日总额度（tokens）；</li>
 *   <li>{@code x-quota-used}：当日已用（tokens）；</li>
 *   <li>{@code x-quota-remaining}：当日剩余（tokens）；</li>
 *   <li>{@code x-quota-reset}：重置时间点（ISO 8601，UTC 零点）；</li>
 *   <li>{@code x-quota-fast-limit} / {@code x-quota-fast-remaining}：fast 档限额与剩余。</li>
 * </ul>
 *
 * <p>读数契约：parts[0]=今日剩余、parts[1]=今日已用（两段之和即当日限额），
 * 页面走「剩余 / 总量」进度条；单位「tokens」。限额、重置时间、fast 档配额放
 * {@code extra}。只返回单边读数时按「限额 − 另一边」补齐；连配额头都没有
 * （令牌错误、接口变更）时按失败返回，不把报错当余额。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
@Spi("100milionseek")
public class HmilionSeekCreditQueryProvider implements CreditQueryProvider {

    /**
     * 模型列表路径：OpenAI 兼容，Bearer Key 鉴权，响应头携带每日配额
     */
    private static final String MODELS_PATH = "/v1/models";

    /**
     * 额度单位：100milionseek 每日配额按 tokens 计
     */
    private static final String UNIT = "tokens";

    /**
     * 当日总额度响应头
     */
    private static final String H_LIMIT = "x-quota-limit";

    /**
     * 当日已用响应头
     */
    private static final String H_USED = "x-quota-used";

    /**
     * 当日剩余响应头
     */
    private static final String H_REMAINING = "x-quota-remaining";

    /**
     * 配额重置时间响应头（ISO 8601 时间点）
     */
    private static final String H_RESET = "x-quota-reset";

    /**
     * fast 档当日总额度响应头
     */
    private static final String H_FAST_LIMIT = "x-quota-fast-limit";

    /**
     * fast 档当日剩余响应头
     */
    private static final String H_FAST_REMAINING = "x-quota-fast-remaining";

    @Override
    public String name() {
        return "100milionseek";
    }

    /**
     * 查每日配额：GET {base}/v1/models，从响应头读 x-quota-* 配额。
     *
     * <p>模型列表接口本身不消耗推理配额；请求超限（429）时配额头通常仍在，
     * 所以用 {@link CreditHttp#exchange} 拿完整响应，不按「非 2xx 即失败」裁剪。</p>
     *
     * @param query 请求
     * @return 结果；响应头里没有任何配额时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            String base = stripVersionPrefix(query.getUrl());
            if (base == null) {
                return CreditResult.fail(name(), "未配置 100milionseek 上游地址");
            }
            String url = CreditHttp.join(base, MODELS_PATH);
            ClientResponse response = CreditHttp.exchange(query, url);
            BigDecimal limit = headerValue(response, H_LIMIT);
            BigDecimal used = headerValue(response, H_USED);
            BigDecimal remaining = headerValue(response, H_REMAINING);
            if (limit == null && used == null && remaining == null) {
                if (!response.isSuccess()) {
                    return CreditResult.fail(name(), "HTTP " + response.getStatusCode()
                            + " " + snippet(response.getBodyString()));
                }
                return CreditResult.fail(name(), "响应未带 x-quota-* 配额头，无法读出每日额度");
            }
            // 单边读数按「限额 − 另一边」补齐，保持「剩余 + 已用 = 限额」的页面口径
            if (remaining == null && limit != null && used != null) {
                remaining = limit.subtract(used);
            }
            if (used == null && limit != null && remaining != null) {
                used = limit.subtract(remaining);
            }
            List<BalancePart> parts = new ArrayList<>(2);
            if (remaining != null) {
                parts.add(part("今日剩余", remaining.signum() < 0 ? BigDecimal.ZERO : remaining));
            }
            if (used != null) {
                parts.add(part("今日已用", used.signum() < 0 ? BigDecimal.ZERO : used));
            }
            if (parts.isEmpty() && limit != null) {
                // 只有限额：退而给一段可展示的读数
                parts.add(part("今日限额", limit));
            }
            if (parts.isEmpty()) {
                return CreditResult.fail(name(), "响应头里没有可解析的配额数值");
            }
            CreditResult result = CreditResult.ok(name(), parts, response.getBodyString());
            Map<String, Object> extra = new LinkedHashMap<>(8);
            extra.put("unit", UNIT);
            if (limit != null) {
                extra.put("limit", limit);
            }
            if (used != null) {
                extra.put("used", used);
            }
            if (remaining != null) {
                extra.put("remaining", remaining);
            }
            String reset = response.getHeader(H_RESET);
            if (reset != null && !reset.isBlank()) {
                extra.put("reset", reset.trim());
            }
            BigDecimal fastLimit = headerValue(response, H_FAST_LIMIT);
            BigDecimal fastRemaining = headerValue(response, H_FAST_REMAINING);
            if (fastLimit != null) {
                extra.put("fast_limit", fastLimit);
            }
            if (fastRemaining != null) {
                extra.put("fast_remaining", fastRemaining);
            }
            result.setExtra(extra);
            return result;
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 读一个数值型响应头（配额都是整数 tokens）。
     *
     * @param response 响应
     * @param name     头名
     * @return 数值；头缺失或不是数字时为 null
     */
    private static BigDecimal headerValue(ClientResponse response, String name) {
        String value = response.getHeader(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 组装一段读数（单位 tokens）。
     *
     * @param label  段名
     * @param amount 数量
     * @return 读数
     */
    private static BalancePart part(String label, BigDecimal amount) {
        BalancePart part = new BalancePart();
        part.setLabel(label);
        part.setAmount(amount);
        part.setCurrency(UNIT);
        return part;
    }

    /**
     * 剥掉地址尾部的 {@code /v1} 接口前缀，得到站点根地址。
     *
     * <p>供应商行里的上游地址可能是 {@code https://100milionseek.top/v1}
     * 或不带前缀的根地址，都能推出根。</p>
     *
     * @param url 原始地址
     * @return 站点根地址；地址缺失时为 null
     */
    private static String stripVersionPrefix(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String text = url.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith("/v1")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.isBlank() ? null : text;
    }

    /**
     * 截取响应片段用于错误提示。
     *
     * @param text 响应
     * @return 片段
     */
    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200);
    }
}
