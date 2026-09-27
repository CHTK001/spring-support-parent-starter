package com.chua.starter.eduai.collect.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 前置处理器：把任意来源的数据转成「一行一个」的行集合。
 *
 * <p>支持两种方式：</p>
 * <ul>
 *   <li><b>jsonpath</b>：数组定位表达式，支持 {@code $[*]}、{@code $.data.list[*]}、{@code $.data.list}（点号路径 + [*]）</li>
 *   <li><b>groovy</b>：脚本以 {@code data}（解析后的 JSON：List/Map）入参，返回 List&lt;Map&gt; 行集合</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public final class PreProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PreProcessor() {
    }

    /**
     * 执行前置处理（纯映射版，推荐）：映射表 key=JSONPath、value=输出给入库的字段名。
     *
     * <p>对每个 JSONPath 取值（数组则按元素展开），再按下标把各列「拉链」成行——
     * 即「一行一个」：<code>{"$[*].word": "word", "$[*].pinyin": "pinyin"}</code>。</p>
     *
     * @param mappingJson 映射（JSON：JSONPath → 输出名）
     * @param raw         原始数据（JSON 文本，或已是 List/Map）
     * @return 行集合
     */
    public static List<Map<String, Object>> toRowsByMapping(String mappingJson, Object raw) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, String> mapping = parseMapping(mappingJson);
        if (mapping.isEmpty()) {
            return rows;
        }
        JsonNode root = toTree(raw);
        if (root == null) {
            return rows;
        }
        List<String> names = new ArrayList<>();
        List<List<Object>> columns = new ArrayList<>();
        int maxSize = 0;
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            List<Object> values = evaluate(entry.getKey(), root);
            names.add(entry.getValue());
            columns.add(values);
            maxSize = Math.max(maxSize, values.size());
        }
        for (int i = 0; i < maxSize; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 0; c < names.size(); c++) {
                List<Object> values = columns.get(c);
                if (i < values.size() && values.get(i) != null) {
                    row.put(names.get(c), values.get(i));
                }
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 求值 JSONPath（支持 {@code $}、{@code .name}、{@code [*]} 数组展开）。
     *
     * @param path JSONPath
     * @param root 根节点
     * @return 值的列表（叶子为普通对象）
     */
    private static List<Object> evaluate(String path, JsonNode root) {
        List<JsonNode> current = new ArrayList<>();
        current.add(root);
        String expr = path == null ? "" : path.trim();
        if (expr.startsWith("$")) {
            expr = expr.substring(1);
        }
        if (expr.startsWith(".")) {
            expr = expr.substring(1);
        }
        for (String rawSegment : expr.split("\\.")) {
            if (rawSegment.isBlank()) {
                continue;
            }
            boolean expand = rawSegment.contains("[*]");
            String name = rawSegment.replace("[*]", "");
            List<JsonNode> next = new ArrayList<>();
            for (JsonNode node : current) {
                if (node == null) {
                    continue;
                }
                JsonNode target = name.isEmpty() ? node : node.get(name);
                if (target == null || target.isNull()) {
                    continue;
                }
                if (expand && target.isArray()) {
                    target.forEach(next::add);
                } else {
                    next.add(target);
                }
            }
            current = next;
        }
        List<Object> values = new ArrayList<>();
        for (JsonNode node : current) {
            values.add(toValue(node));
        }
        return values;
    }

    /**
     * JSON 节点转普通值（对象/数组转 JSON 文本）。
     *
     * @param node 节点
     * @return 值
     */
    private static Object toValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject() || node.isArray()) {
            return node.toString();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        return node.asText();
    }

    /**
     * 执行前置处理（兼容旧签名：jsonpath/groovy + 可选映射）。
     *
     * @param mode        jsonpath / groovy
     * @param expr        JSONPath 表达式（mode=jsonpath 时使用）
     * @param script      Groovy 脚本（mode=groovy 时使用）
     * @param mappingJson 字段映射（JSON：行 key → 源字段路径；可空）
     * @param raw         原始数据（JSON 文本，或已是 List/Map）
     * @return 行集合（一行一个 Map）
     */
    public static List<Map<String, Object>> toRows(String mode, String expr, String script,
                                                   String mappingJson, Object raw) {
        if ("groovy".equalsIgnoreCase(mode)) {
            return byGroovy(script, raw);
        }
        List<Map<String, Object>> rows = byJsonPath(expr, raw);
        Map<String, String> mapping = parseMapping(mappingJson);
        if (mapping.isEmpty()) {
            return rows;
        }
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> out = new LinkedHashMap<>();
            mapping.forEach((key, path) -> {
                Object value = valueOf(row, path);
                if (value != null) {
                    out.put(key, value);
                }
            });
            if (!out.isEmpty()) {
                mapped.add(out);
            }
        }
        return mapped;
    }

    /**
     * 兼容旧签名（无映射）。
     *
     * @param mode   jsonpath / groovy
     * @param expr   JSONPath
     * @param script Groovy 脚本
     * @param raw    原始数据
     * @return 行集合
     */
    public static List<Map<String, Object>> toRows(String mode, String expr, String script, Object raw) {
        return toRows(mode, expr, script, null, raw);
    }

    /**
     * 解析字段映射（行 key → 源字段路径）。
     *
     * @param mappingJson JSON 文本
     * @return 映射表
     */
    private static Map<String, String> parseMapping(String mappingJson) {
        Map<String, String> map = new LinkedHashMap<>();
        if (!StringUtils.hasText(mappingJson)) {
            return map;
        }
        try {
            JsonNode node = MAPPER.readTree(mappingJson);
            if (node.isObject()) {
                node.fields().forEachRemaining(e -> {
                    if (e.getValue() != null && !e.getValue().isNull()) {
                        map.put(e.getKey(), e.getValue().asText());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("[collect] 前置处理器字段映射解析失败: {}", e.getMessage());
        }
        return map;
    }

    /**
     * 按点号路径取值。
     *
     * @param row  行数据
     * @param path 路径
     * @return 值，缺失返回 null
     */
    private static Object valueOf(Map<String, Object> row, String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        Object current = row;
        for (String segment : path.trim().split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<?, ?>) map).get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * JSONPath 取数（支持 $[*]、$.a.b[*]、$.a.b 的数组子集）。
     *
     * @param expr 表达式
     * @param raw  原始数据
     * @return 行集合
     */
    private static List<Map<String, Object>> byJsonPath(String expr, Object raw) {
        List<Map<String, Object>> rows = new ArrayList<>();
        JsonNode root = toTree(raw);
        if (root == null) {
            return rows;
        }
        JsonNode node = root;
        String path = expr == null ? "" : expr.trim();
        if (path.startsWith("$")) {
            path = path.substring(1);
        }
        path = path.replace("[*]", "");
        if (path.startsWith(".")) {
            path = path.substring(1);
        }
        for (String segment : path.split("\\.")) {
            if (node == null || segment.isBlank()) {
                continue;
            }
            node = node.get(segment);
        }
        if (node == null) {
            return rows;
        }
        if (node.isArray()) {
            node.forEach(item -> rows.add(toRow(item)));
        } else if (node.isObject()) {
            rows.add(toRow(node));
        }
        return rows;
    }

    /**
     * Groovy 前置处理：脚本入参 data，返回 List&lt;Map&gt;（也接受单个 Map）。
     *
     * @param script 脚本
     * @param raw    原始数据
     * @return 行集合
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> byGroovy(String script, Object raw) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!StringUtils.hasText(script)) {
            return rows;
        }
        try {
            Object data = raw;
            if (raw instanceof String text) {
                data = MAPPER.readValue(text, Object.class);
            }
            Binding binding = new Binding();
            binding.setVariable("data", data);
            binding.setVariable("log", log);
            Object result = new GroovyShell(binding).evaluate(script);
            if (result instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        rows.add((Map<String, Object>) map);
                    } else if (item != null) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("value", item);
                        rows.add(row);
                    }
                }
            } else if (result instanceof Map<?, ?> map) {
                rows.add((Map<String, Object>) map);
            }
        } catch (Exception e) {
            throw new IllegalStateException("前置处理器(Groovy)执行失败: " + e.getMessage(), e);
        }
        return rows;
    }

    /**
     * 单个 JSON 节点转行（对象字段平铺；嵌套对象/数组转 JSON 文本）。
     *
     * @param node 节点
     * @return 行
     */
    private static Map<String, Object> toRow(JsonNode node) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return row;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                JsonNode value = e.getValue();
                if (value == null || value.isNull()) {
                    return;
                }
                if (value.isObject() || value.isArray()) {
                    row.put(e.getKey(), value.toString());
                } else if (value.isBoolean()) {
                    row.put(e.getKey(), value.asBoolean());
                } else if (value.isNumber()) {
                    row.put(e.getKey(), value.numberValue());
                } else {
                    row.put(e.getKey(), value.asText());
                }
            });
        }
        return row;
    }

    private static JsonNode toTree(Object raw) {
        try {
            if (raw == null) {
                return null;
            }
            if (raw instanceof JsonNode node) {
                return node;
            }
            if (raw instanceof String text) {
                return MAPPER.readTree(text);
            }
            return MAPPER.valueToTree(raw);
        } catch (Exception e) {
            log.warn("[collect] 前置处理解析失败: {}", e.getMessage());
            return null;
        }
    }
}
