package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditExpressions;
import com.chua.starter.datasearch.credit.support.CreditHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容计费接口（one-api / new-api / voapi / veloera 等中转站通用）。
 *
 * <p>这类站点沿用 OpenAI 老的 dashboard 计费口径，只要站点根地址即可：</p>
 * <ul>
 *   <li>{@code GET /v1/dashboard/billing/subscription} → {@code hard_limit_usd /
 *       system_hard_limit_usd / soft_limit_usd}，单位是美元，表示本期额度上限；</li>
 *   <li>{@code GET /v1/dashboard/billing/usage?start_date=&amp;end_date=} →
 *       {@code total_usage}，单位是「美分」（OpenAI 原文如此，站点若按美元返回会差 100 倍）。</li>
 * </ul>
 *
 * <p>剩余 = 上限 − 已用 / 100。两次请求都必须成功才给结论：只拿到上限时不拿它冒充余额，
 * 而是把「用量接口不可用」原样报出来，页面据此显示真实原因。</p>
 *
 * <p>站点只回其中一个接口、或字段口径不同，就在页面上改用 {@code expression} 实现自填地址；
 * 这里不猜字段。{@code expression} 非空时只发 subscription 一次，按表达式解析。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Spi("billing")
public class BillingCreditQueryProvider implements CreditQueryProvider {

    /**
     * 额度上限接口路径
     */
    private static final String SUBSCRIPTION_PATH = "/v1/dashboard/billing/subscription";

    /**
     * 已用额度接口路径
     */
    private static final String USAGE_PATH = "/v1/dashboard/billing/usage";

    /**
     * 站点根地址里需要剥掉的后缀（长的在前）
     */
    private static final String[] BASE_SUFFIX = {SUBSCRIPTION_PATH, USAGE_PATH,
            "/dashboard/billing/subscription", "/dashboard/billing/usage",
            "/v1/dashboard/billing", "/dashboard/billing", "/v1", "/api"};

    /**
     * 上限字段的候选名（按优先级）
     */
    private static final String[] LIMIT_FIELDS = {"hard_limit_usd", "system_hard_limit_usd", "soft_limit_usd"};

    /**
     * 已用字段名
     */
    private static final String USAGE_FIELD = "total_usage";

    /**
     * 美分换算
     */
    private static final BigDecimal CENTS = new BigDecimal(100);

    /**
     * 币种
     */
    private static final String CURRENCY = "USD";

    /**
     * 查询窗口：最近 30 天（OpenAI 老口径按自然日）
     */
    private static final int WINDOW_DAYS = 30;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String name() {
        return "billing";
    }

    /**
     * 按 OpenAI 兼容计费口径查询余额。
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            String root = base(query.getUrl());
            String raw = CreditHttp.fetch(query, CreditHttp.join(root, SUBSCRIPTION_PATH));
            String expression = query.getExpression();
            if (expression != null && !expression.isBlank()) {
                return CreditExpressions.parse(expression, raw, name(), query.getBilling());
            }
            BigDecimal limit = number(raw, LIMIT_FIELDS);
            if (limit == null) {
                return CreditResult.fail(name(), "上游未返回额度上限字段（hard_limit_usd）");
            }
            BigDecimal usedCents;
            try {
                usedCents = number(CreditHttp.fetch(query, usageUrl(root)), USAGE_FIELD);
            } catch (RuntimeException e) {
                return CreditResult.fail(name(), "额度上限 " + plain(limit) + " USD，但用量接口不可用: " + e.getMessage());
            }
            if (usedCents == null) {
                return CreditResult.fail(name(), "额度上限 " + plain(limit) + " USD，但用量接口未返回 total_usage");
            }
            BigDecimal used = usedCents.divide(CENTS, 6, RoundingMode.HALF_UP);
            BigDecimal remain = limit.subtract(used);
            List<BalancePart> parts = new ArrayList<>(3);
            parts.add(part("剩余额度", remain));
            parts.add(part("已用额度", used));
            parts.add(part("额度上限", limit));
            CreditResult result = CreditResult.ok(name(), parts, raw);
            result.setSource(name());
            return result;
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 拼用量接口地址（OpenAI 老口径要求给起止日期）。
     *
     * @param root 站点根地址
     * @return 完整地址
     */
    private static String usageUrl(String root) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(WINDOW_DAYS);
        return CreditHttp.join(root, USAGE_PATH) + "?start_date=" + start + "&end_date=" + end;
    }

    /**
     * 构造一段读数。
     *
     * @param label  段名
     * @param amount 金额
     * @return 读数段
     */
    private static BalancePart part(String label, BigDecimal amount) {
        BalancePart part = new BalancePart();
        part.setLabel(label);
        part.setAmount(amount.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros());
        part.setCurrency(CURRENCY);
        return part;
    }

    /**
     * 从响应里取第一个可用的数值字段。
     *
     * @param raw    响应正文
     * @param fields 候选字段名
     * @return 数值；都取不到时为 null
     */
    private static BigDecimal number(String raw, String... fields) {
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception e) {
            return null;
        }
        if (root == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode node = root.get(field);
            if (node == null || node.isNull()) {
                continue;
            }
            if (node.isNumber()) {
                return node.decimalValue();
            }
            if (node.isTextual()) {
                try {
                    return new BigDecimal(node.asText().trim());
                } catch (NumberFormatException ignored) {
                    // 该字段不是数字，继续看下一个候选
                }
            }
        }
        return null;
    }

    /**
     * 数值转原样文本（错误提示用，避免科学计数法）。
     *
     * @param value 数值
     * @return 文本
     */
    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * 剥掉站点根地址上多余的后缀。
     *
     * @param url 原始地址
     * @return 站点根地址
     */
    private static String base(String url) {
        if (url == null) {
            return null;
        }
        String text = url.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        for (String suffix : BASE_SUFFIX) {
            if (text.endsWith(suffix)) {
                text = text.substring(0, text.length() - suffix.length());
                break;
            }
        }
        return text;
    }
}
