package com.chua.starter.spider.flow.handler;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.spider.entity.SpiderFlowNode;
import com.chua.starter.spider.flow.NodeHandler;
import com.chua.starter.spider.flow.SpiderFlowContext;
import com.chua.starter.spider.flow.SpiderFlowUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据抽取节点处理器：从上一个节点保存的变量里抽取字段，支持 XPath/JsonPath/正则/简单文本。
 *
 * <p>config JSON 结构：</p>
 * <pre>
 * {
 *   "source": "resp",                // 源变量名（默认 resp）
 *   "extractorType": "jsonpath",     // jsonpath | regex | text
 *   "expression": "$.data.items",    // 抽取表达式
 *   "saveAs": "items",               // 结果变量名
 *   "isList": true                   // 是否列表（默认 false）
 * }
 * </pre>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class ExtractNodeHandler implements NodeHandler {

    /**
     * 抽取器类型：JSONPath
     */
    private static final String TYPE_JSONPATH = "jsonpath";

    /**
     * 抽取器类型：正则
     */
    private static final String TYPE_REGEX = "regex";

    /**
     * 抽取器类型：文本
     */
    private static final String TYPE_TEXT = "text";

    /**
     * 节点类型编码
     */
    private static final String TYPE = "extract";

    /**
     * 源变量名默认值
     */
    private static final String DEFAULT_SOURCE = "resp";

    /**
     * 抽取器类型默认值
     */
    private static final String DEFAULT_EXTRACTOR_TYPE = TYPE_JSONPATH;

    /**
     * JSON 解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String supportedType() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(SpiderFlowNode node, SpiderFlowContext context) throws Exception {
        Map<String, Object> config = parseConfig(node.getFlowNodeConfig());

        String source = SpiderFlowUtils.defaultIfEmpty(asString(config.get("source")), DEFAULT_SOURCE);
        String extractorType = SpiderFlowUtils.defaultIfEmpty(asString(config.get("extractorType")), DEFAULT_EXTRACTOR_TYPE);
        String expression = asString(config.get("expression"));
        String saveAs = SpiderFlowUtils.defaultIfEmpty(asString(config.get("saveAs")), "ext_" + node.getFlowNodeKey());
        boolean isList = Boolean.TRUE.equals(config.get("isList"));

        Object sourceObj = context.getVar(source);
        if (sourceObj == null) {
            log.warn("[Flow {}] 抽取源变量为空: {}", context.getExecutionNo(), source);
            return "empty source";
        }

        Object result;
        switch (extractorType.toLowerCase()) {
            case TYPE_REGEX -> result = extractByRegex(asString(sourceObj), expression, isList);
            case TYPE_TEXT -> result = extractByText(asString(sourceObj), expression, isList);
            case TYPE_JSONPATH -> result = extractByJsonPath(asString(sourceObj), expression, isList);
            default -> result = extractByJsonPath(asString(sourceObj), expression, isList);
        }
        context.putVar(saveAs, result);
        log.info("[Flow {}] 抽取 {}={} -> {} ({} items)",
                context.getExecutionNo(), source, extractorType, saveAs,
                result instanceof List<?> list ? list.size() : 1);
        return saveAs;
    }

    /**
     * JSONPath 抽取：支持简单 {@code $.a.b[0]} 语法（自实现，不依赖 jsonpath 库）。
     */
    private Object extractByJsonPath(String json, String expression, boolean isList) {
        try {
            Object root = objectMapper.readValue(json, Object.class);
            Object result = navigateJsonPath(root, expression);
            if (result == null) {
                return null;
            }
            if (isList && !(result instanceof List)) {
                return new ArrayList<>(java.util.Collections.singletonList(result));
            }
            return result;
        } catch (Exception e) {
            log.warn("[Extract] JSONPath 失败: path={}, err={}", expression, e.getMessage());
            return null;
        }
    }

    /**
     * 简单 JSONPath 导航：支持 {@code $}, {@code $.a}, {@code $.a.b}, {@code $.a[0]}, {@code $.items[*]}。
     */
    @SuppressWarnings("unchecked")
    private Object navigateJsonPath(Object root, String expression) {
        if (expression == null || expression.isEmpty()) {
            return root;
        }
        String expr = expression.startsWith("$") ? expression.substring(1) : expression;
        if (expr.startsWith(".")) {
            expr = expr.substring(1);
        }
        if (expr.isEmpty()) {
            return root;
        }
        Object current = root;
        int i = 0;
        while (i < expr.length() && current != null) {
            char c = expr.charAt(i);
            if (c == '.') {
                i++;
                continue;
            }
            if (c == '[') {
                int end = expr.indexOf(']', i);
                if (end < 0) {
                    return null;
                }
                String idx = expr.substring(i + 1, end).trim();
                i = end + 1;
                if (idx.equals("*")) {
                    if (current instanceof List<?> list) {
                        return list;
                    }
                    return null;
                }
                int n;
                try {
                    n = Integer.parseInt(idx);
                } catch (NumberFormatException ex) {
                    return null;
                }
                if (current instanceof List<?> list) {
                    current = n >= 0 && n < list.size() ? list.get(n) : null;
                } else {
                    return null;
                }
            } else {
                // 取连续 key（直到下一个 . 或 [）
                int j = i;
                while (j < expr.length() && expr.charAt(j) != '.' && expr.charAt(j) != '[') {
                    j++;
                }
                String key = expr.substring(i, j);
                i = j;
                if (current instanceof Map<?, ?> map) {
                    current = map.get(key);
                } else {
                    return null;
                }
            }
        }
        return current;
    }

    /**
     * 正则抽取（取 group(0) 列表）。
     */
    private Object extractByRegex(String text, String expression, boolean isList) {
        if (text == null || expression == null) {
            return null;
        }
        Pattern p = Pattern.compile(expression);
        Matcher m = p.matcher(text);
        List<String> matches = new ArrayList<>();
        while (m.find()) {
            matches.add(m.group());
        }
        if (!isList && matches.size() == 1) {
            return matches.get(0);
        }
        return matches;
    }

    /**
     * 简单文本截取（substring(from, to)，from/to 是数字字符串）。
     */
    private Object extractByText(String text, String expression, boolean isList) {
        if (text == null || expression == null) {
            return null;
        }
        List<String> tokens = splitComma(expression);
        if (tokens.size() >= 2) {
            try {
                int from = Integer.parseInt(tokens.get(0).trim());
                int to = Integer.parseInt(tokens.get(1).trim());
                String sub = text.substring(Math.min(from, text.length()), Math.min(to, text.length()));
                return isList ? java.util.Collections.singletonList(sub) : sub;
            } catch (NumberFormatException ignored) {
                // 忽略，返回 null
            }
        }
        return null;
    }

    /**
     * 简单逗号分隔（无依赖）。
     */
    private List<String> splitComma(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : text.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * 解析 config JSON。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String json) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[Extract] config JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Object 转 String。
     */
    private String asString(Object o) {
        return o == null ? null : o.toString();
    }
}


