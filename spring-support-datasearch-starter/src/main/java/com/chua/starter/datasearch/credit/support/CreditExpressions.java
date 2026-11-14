package com.chua.starter.datasearch.credit.support;

import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 余额字段表达式求值。
 *
 * <p>各家余额接口的返回结构差异很大，用一条表达式代替「给每家写一段解析代码」：</p>
 * <pre>
 * 货币前缀   $ / ¥ / ￥ / € / £       → 该段币种，缺省时用计费币种
 * 字段路径   data.user_quota.total    → 点路径，数字段按数组下标，* 为通配展开
 * 单位换算   ... / 500000             → 除法（含空格），同理 * 2
 * 百分比     ... %                    → 原值是比例，乘 100 后按百分比展示
 * 多段       $remaining; $used        → 分号分隔，逐段读出
 * </pre>
 *
 * <p>示例：{@code $data.user_quota.total_available / 500000}。</p>
 *
 * @author CH
 * @since 4.0.0.54
 */
public final class CreditExpressions {

    /**
     * 币种前缀
     */
    private static final Map<Character, String> CURRENCY_PREFIX = new LinkedHashMap<>();

    /**
     * 计费币种别名
     */
    private static final Map<String, String> BILLING_ALIAS = new LinkedHashMap<>();

    /**
     * 换算因子：要求运算符两侧有空格，避免与路径通配 * 混淆
     */
    private static final Pattern SCALE = Pattern.compile("^(.*?)\\s+([*/])\\s+(\\d+(?:\\.\\d+)?)$");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PART_SEPARATOR = ";";

    private static final String PATH_SPLITTER = "[.\\[\\]]+";

    private static final String WILDCARD = "*";

    private static final String PERCENT = "%";

    private static final String PERCENT_MULTIPLIER = "100";

    private static final int SCALE_DIGITS = 6;

    static {
        CURRENCY_PREFIX.put('$', "USD");
        CURRENCY_PREFIX.put('¥', "CNY");
        CURRENCY_PREFIX.put('￥', "CNY");
        CURRENCY_PREFIX.put('€', "EUR");
        CURRENCY_PREFIX.put('£', "GBP");
        BILLING_ALIAS.put("usd", "USD");
        BILLING_ALIAS.put("usdollar", "USD");
        BILLING_ALIAS.put("cny", "CNY");
        BILLING_ALIAS.put("rmb", "CNY");
        BILLING_ALIAS.put("cnyrmb", "CNY");
        BILLING_ALIAS.put("cny/rmb", "CNY");
    }

    private CreditExpressions() {
    }

    /**
     * 按表达式从原始响应里取值。
     *
     * @param expression 表达式
     * @param raw        原始响应
     * @param source     实现标识
     * @param billing    计费币种口径，可为空
     * @return 结果；解析失败时 error 有值
     */
    public static CreditResult parse(String expression, String raw, String source, String billing) {
        if (expression == null || expression.isBlank()) {
            return CreditResult.fail(source, "未配置字段表达式");
        }
        if (raw == null || raw.isBlank()) {
            return CreditResult.fail(source, "响应为空");
        }
        final JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception e) {
            return CreditResult.fail(source, "响应不是合法 JSON: " + e.getMessage());
        }
        String fallbackCurrency = normalizeBilling(billing);
        List<BalancePart> parts = new ArrayList<>();
        for (String segment : expression.split(PART_SEPARATOR)) {
            String text = segment.trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                parts.addAll(evaluate(root, text, fallbackCurrency));
            } catch (RuntimeException e) {
                return CreditResult.fail(source, "表达式解析失败: " + text + " (" + e.getMessage() + ")");
            }
        }
        if (parts.isEmpty()) {
            return CreditResult.fail(source, "表达式未取到数值: " + expression);
        }
        return CreditResult.ok(source, parts, raw);
    }

    /**
     * 求单个表达式段。
     *
     * @param root             响应根节点
     * @param segment          表达式段
     * @param fallbackCurrency 兜底币种
     * @return 该段读出的全部读数
     */
    private static List<BalancePart> evaluate(JsonNode root, String segment, String fallbackCurrency) {
        String text = segment.trim();
        String currency = null;
        if (!text.isEmpty() && CURRENCY_PREFIX.containsKey(text.charAt(0))) {
            currency = CURRENCY_PREFIX.get(text.charAt(0));
            text = text.substring(1).trim();
        }
        boolean percent = text.endsWith(PERCENT);
        if (percent) {
            text = text.substring(0, text.length() - 1).trim();
        }
        BigDecimal factor = null;
        String operator = null;
        Matcher matcher = SCALE.matcher(text);
        if (matcher.matches()) {
            text = matcher.group(1).trim();
            operator = matcher.group(2);
            factor = new BigDecimal(matcher.group(3));
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("缺少字段路径");
        }
        List<Hit> hits = new ArrayList<>();
        walk(root, text.split(PATH_SPLITTER), 0, "", hits);
        List<BalancePart> parts = new ArrayList<>(hits.size());
        for (Hit hit : hits) {
            BigDecimal value = decimalOf(hit.node);
            if (value == null) {
                continue;
            }
            if (factor != null) {
                value = "*".equals(operator) ? value.multiply(factor) : value.divide(factor, SCALE_DIGITS, RoundingMode.HALF_UP);
            }
            if (percent) {
                value = value.multiply(new BigDecimal(PERCENT_MULTIPLIER));
            }
            BalancePart part = new BalancePart();
            part.setLabel(hit.label.isEmpty() ? text : hit.label);
            part.setAmount(value.setScale(SCALE_DIGITS, RoundingMode.HALF_UP).stripTrailingZeros());
            part.setCurrency(percent ? PERCENT : (currency != null ? currency : fallbackCurrency));
            parts.add(part);
        }
        return parts;
    }

    /**
     * 沿路径下钻，通配 * 展开成多段。
     *
     * @param node   当前节点
     * @param tokens 路径分段
     * @param index  当前分段下标
     * @param label  已拼出的段名
     * @param hits   命中结果
     */
    private static void walk(JsonNode node, String[] tokens, int index, String label, List<Hit> hits) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (index >= tokens.length) {
            hits.add(new Hit(label, node));
            return;
        }
        String token = tokens[index];
        if (WILDCARD.equals(token)) {
            if (node.isArray()) {
                for (int i = 0; i < node.size(); i++) {
                    walk(node.get(i), tokens, index + 1, label.isEmpty() ? String.valueOf(i) : label + "[" + i + "]", hits);
                }
            } else if (node.isObject()) {
                node.fields().forEachRemaining(entry -> walk(entry.getValue(), tokens, index + 1,
                        label.isEmpty() ? entry.getKey() : label + "." + entry.getKey(), hits));
            }
            return;
        }
        JsonNode child;
        if (node.isArray()) {
            if (!token.matches("\\d+")) {
                return;
            }
            int position = Integer.parseInt(token);
            child = position < node.size() ? node.get(position) : null;
        } else {
            child = node.get(token);
        }
        if (child == null) {
            return;
        }
        walk(child, tokens, index + 1, label.isEmpty() ? token : label + "." + token, hits);
    }

    /**
     * 取节点数值；文本按数字解析（去掉千分位与常见货币符号）。
     *
     * @param node 节点
     * @return 数值；无法解析时返回空
     */
    private static BigDecimal decimalOf(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (!node.isTextual()) {
            return null;
        }
        String text = node.asText().trim().replace(",", "");
        if (text.isEmpty()) {
            return null;
        }
        if (CURRENCY_PREFIX.containsKey(text.charAt(0))) {
            text = text.substring(1).trim();
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 归一计费币种口径。
     *
     * @param billing 原始口径
     * @return 币种；无法识别时为 null
     */
    private static String normalizeBilling(String billing) {
        if (billing == null || billing.isBlank()) {
            return null;
        }
        String key = billing.trim().toLowerCase();
        String mapped = BILLING_ALIAS.get(key);
        if (mapped != null) {
            return mapped;
        }
        return key.length() <= 4 ? key.toUpperCase() : null;
    }

    /**
     * 路径命中：段名与节点。
     *
     * @param label 段名
     * @param node  节点
     */
    private static final class Hit {

        private final String label;

        private final JsonNode node;

        private Hit(String label, JsonNode node) {
            this.label = label;
            this.node = node;
        }
    }
}
