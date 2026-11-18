package com.chua.starter.datasearch.credit.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.datasearch.credit.model.BalancePart;
import com.chua.starter.datasearch.credit.model.CreditMapping;
import com.chua.starter.datasearch.credit.model.CreditQuery;
import com.chua.starter.datasearch.credit.model.CreditResult;
import com.chua.starter.datasearch.credit.spi.CreditQueryProvider;
import com.chua.starter.datasearch.credit.support.CreditExpressions;
import com.chua.starter.datasearch.credit.support.CreditHttp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义余额实现：字段映射驱动，支持 JSONPath 与 XPath。
 *
 * <p>页面「自定义映射」把上游响应里的位置逐字段映射到后端要的读数上：
 * {@code remain}（剩余）、{@code used}（已用）、{@code currency}（币种，可选）。
 * 表达式以 {@code /} 开头按 XPath 解析（响应须是 XML），其余按 JSONPath 解析
 * （{@code $.data.balance}、{@code $..balance}、{@code $.a[0].b} 等）。</p>
 *
 * <p>没配映射时回落到旧的整条表达式口径（{@link CreditExpressions}），
 * 老配置不因本次改造失效。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
@Spi("custom")
public class CustomCreditQueryProvider implements CreditQueryProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * XPath 表达式的判定前缀
     */
    private static final String XPATH_ROOT = "/";

    /**
     * JSONPath 表达式的判定前缀
     */
    private static final String JSONPATH_ROOT = "$";

    /**
     * 深度扫描标记（{@code $..field} 归一化而来）
     */
    private static final String DEEP_SCAN = "**";

    /**
     * 通配符
     */
    private static final String WILDCARD = "*";

    /**
     * 币种字段：不产出读数段，只给其余段定币种
     */
    private static final String FIELD_CURRENCY = "currency";

    /**
     * 字段排序：remain 排第一段、used 第二段（页面口径「前一段剩余、后一段已用」），其余跟后
     */
    private static final Map<String, Integer> FIELD_ORDER = Map.of("remain", 0, "used", 1);

    /**
     * 币种别名 → 标准币种
     */
    private static final Map<String, String> CURRENCY_ALIAS = Map.of(
            "$", "USD", "¥", "CNY", "￥", "CNY",
            "usd", "USD", "cny", "CNY", "rmb", "CNY", "eur", "EUR", "gbp", "GBP"
    );

    @Override
    public String name() {
        return "custom";
    }

    /**
     * 按字段映射查询余额。
     *
     * @param query 请求
     * @return 结果；查不到时 error 有值
     */
    @Override
    public CreditResult query(CreditQuery query) {
        try {
            String raw = CreditHttp.fetch(query, query.getUrl());
            if (query.getMapping() == null || query.getMapping().isEmpty()) {
                // 老口径兜底：只有整条表达式时按表达式解析
                return CreditExpressions.parse(query.getExpression(), raw, name(), query.getBilling());
            }
            return evaluate(raw, query.getMapping(), query.getBilling());
        } catch (RuntimeException e) {
            return CreditResult.fail(name(), e.getMessage());
        }
    }

    /**
     * 逐条执行映射，汇成一份读数。
     *
     * @param raw     原始响应
     * @param mapping 字段映射
     * @param billing 计费币种兜底
     * @return 结果
     */
    private CreditResult evaluate(String raw, List<CreditMapping> mapping, String billing) {
        List<BalancePart> parts = new ArrayList<>(mapping.size());
        List<String> failures = new ArrayList<>(mapping.size());
        String currency = null;
        for (CreditMapping entry : mapping) {
            String field = fieldOf(entry);
            String expression = entry.getExpression() == null ? "" : entry.getExpression().trim();
            if (expression.isEmpty()) {
                continue;
            }
            try {
                String text = expression.startsWith(XPATH_ROOT)
                        ? xpathFirst(raw, expression)
                        : jsonPathFirst(raw, expression);
                if (text == null || text.isBlank()) {
                    failures.add(field + ": 未命中");
                    continue;
                }
                if (FIELD_CURRENCY.equals(field)) {
                    currency = normalizeCurrency(text, currency);
                    continue;
                }
                BigDecimal value = toNumber(text);
                if (value == null) {
                    failures.add(field + ": 不是数值");
                    continue;
                }
                parts.add(part(field, value));
            } catch (RuntimeException e) {
                failures.add(field + ": " + e.getMessage());
            }
        }
        if (parts.isEmpty()) {
            return CreditResult.fail(name(), failures.isEmpty()
                    ? "映射未取到数值" : "映射未取到数值: " + String.join("；", failures));
        }
        applyCurrency(parts, currency, billing);
        parts.sort((a, b) -> priority(a.getLabel()) - priority(b.getLabel()));
        return CreditResult.ok(name(), parts, raw);
    }

    /**
     * 字段名归一：小写、去空白；缺失按空串处理。
     *
     * @param entry 映射行
     * @return 字段名
     */
    private static String fieldOf(CreditMapping entry) {
        String field = entry.getField() == null ? "" : entry.getField().trim().toLowerCase();
        return field.isEmpty() ? "remain" : field;
    }

    /**
     * 组一段读数。
     *
     * @param field 字段名（也是展示标签）
     * @param value 数值
     * @return 读数段
     */
    private static BalancePart part(String field, BigDecimal value) {
        BalancePart part = new BalancePart();
        part.setLabel(field);
        part.setAmount(value);
        return part;
    }

    /**
     * 定币种：映射里的币种字段优先，其次计费币种兜底；都没有就保持空（页面不显示币种）。
     *
     * @param parts    读数列表
     * @param currency 币种字段取到的币种
     * @param billing  计费币种兜底
     */
    private static void applyCurrency(List<BalancePart> parts, String currency, String billing) {
        String resolved = currency != null ? currency : CURRENCY_ALIAS.get(
                billing == null ? "" : billing.trim().toLowerCase());
        if (resolved == null) {
            return;
        }
        for (BalancePart part : parts) {
            part.setCurrency(resolved);
        }
    }

    /**
     * 字段展示顺序：remain → used → 其余。
     *
     * @param field 字段名
     * @return 排序优先级
     */
    private static int priority(String field) {
        return FIELD_ORDER.getOrDefault(field == null ? "" : field.trim().toLowerCase(), 2);
    }

    /**
     * 币种归一：符号 / 小写别名统一成标准币种，识别不了原样返回。
     *
     * @param text     原始币种
     * @param fallback 上一次的币种（本次识别不了时保持不变）
     * @return 标准币种
     */
    private static String normalizeCurrency(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String trimmed = text.trim();
        String alias = CURRENCY_ALIAS.get(trimmed);
        if (alias != null) {
            return alias;
        }
        return CURRENCY_ALIAS.getOrDefault(trimmed.toLowerCase(), trimmed);
    }

    /**
     * 数字解析：纯数字或带小数点的文本都收，解析失败返回 null（调用方跳过该段）。
     *
     * @param text 文本
     * @return 数字
     */
    private static BigDecimal toNumber(String text) {
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * JSONPath 取首个命中节点的文本：{@code $.a.b}、{@code $['a'][0]}、{@code $..a}、
     * {@code $.a[*].b}，也兼容不带 {@code $} 的点路径（{@code data.balance}）。
     *
     * @param raw        原始响应（JSON）
     * @param expression 表达式
     * @return 首个命中节点的文本；无命中为 null
     */
    private static String jsonPathFirst(String raw, String expression) {
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + e.getMessage(), e);
        }
        List<JsonNode> hits = new ArrayList<>();
        select(root, normalizeJsonPath(expression).split("\\."), 0, hits);
        if (hits.isEmpty()) {
            return null;
        }
        JsonNode first = hits.get(0);
        return first.isValueNode() ? first.asText() : first.toString();
    }

    /**
     * JSONPath 归一成点路径：{@code $['a']['b']} → {@code a.b}、{@code [0]} → {@code 0}、
     * {@code [*]} → {@code *}、{@code ..} → {@code **}，并去掉首尾的点。
     *
     * @param expression 原始表达式
     * @return 点路径
     */
    private static String normalizeJsonPath(String expression) {
        String path = expression.trim();
        if (path.startsWith(JSONPATH_ROOT)) {
            path = path.substring(1);
        }
        // 引号下标 → 点字段
        path = path.replaceAll("\\['([^']*)'\\]", ".$1");
        path = path.replaceAll("\\[\"([^\"]*)\"\\]", ".$1");
        // 通配下标与数字下标
        path = path.replace("[*]", ".*");
        path = path.replaceAll("\\[(\\d+)\\]", ".$1");
        // 深度扫描：.. → **
        path = path.replace("..", "." + DEEP_SCAN + ".");
        while (path.startsWith(".")) {
            path = path.substring(1);
        }
        while (path.endsWith(".")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * 沿点路径下钻。
     *
     * @param node   当前节点
     * @param tokens 路径分段
     * @param index  当前分段下标
     * @param hits   命中结果
     */
    private static void select(JsonNode node, String[] tokens, int index, List<JsonNode> hits) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (index >= tokens.length) {
            hits.add(node);
            return;
        }
        String token = tokens[index];
        if (DEEP_SCAN.equals(token)) {
            // 深度扫描：当前节点先试匹配剩余路径，再对所有后代重复本段
            select(node, tokens, index + 1, hits);
            if (node.isContainerNode()) {
                for (JsonNode child : node) {
                    select(child, tokens, index, hits);
                }
            }
            return;
        }
        if (WILDCARD.equals(token)) {
            if (node.isArray() || node.isObject()) {
                for (JsonNode child : node) {
                    select(child, tokens, index + 1, hits);
                }
            }
            return;
        }
        if (node.isArray()) {
            try {
                select(node.get(Integer.parseInt(token)), tokens, index + 1, hits);
            } catch (NumberFormatException e) {
                // 数组上用字段名取值：无命中
            }
            return;
        }
        if (node.isObject()) {
            select(node.get(token), tokens, index + 1, hits);
        }
    }

    /**
     * XPath 取首个命中节点的文本。
     *
     * @param raw        原始响应（XML）
     * @param expression XPath 表达式
     * @return 首个节点的文本；无命中为 null
     */
    private static String xpathFirst(String raw, String expression) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            // 禁 DTD / 外部实体，防 XXE：余额地址是用户配的，响应不可信
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
            // 老解析器不认该特性时按默认安全策略继续
        }
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Node node;
        try {
            var document = factory.newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new StringReader(raw)));
            NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                    .evaluate(expression, document, XPathConstants.NODESET);
            node = nodes.getLength() > 0 ? nodes.item(0) : null;
        } catch (Exception e) {
            throw new IllegalStateException("XPath 解析失败: " + e.getMessage(), e);
        }
        if (node == null) {
            return null;
        }
        String text = node.getTextContent();
        return text == null ? null : text.trim();
    }
}
