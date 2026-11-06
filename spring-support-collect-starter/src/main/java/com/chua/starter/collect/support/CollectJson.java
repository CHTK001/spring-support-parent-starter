package com.chua.starter.collect.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON 工具。
 *
 * <p>DSL 存取、结果 payload 序列化与 JSON 抽取共用一个宽松配置的 ObjectMapper。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CollectJson {

    /**
     * 共享映射器，忽略未知字段以免流程定义升级后读不出。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    /**
     * 下标片段匹配，形如 {@code [0][2]}。
     */
    private static final Pattern SUBSCRIPT = Pattern.compile("\\[(\\d+)]");

    /**
     * 工具类禁止实例化。
     */
    private CollectJson() {
    }

    /**
     * 对象转 JSON 文本。
     *
     * @param value 对象
     * @return JSON 文本，失败返回空串
     */
    public static String toJson(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    /**
     * JSON 文本转指定类型。
     *
     * @param json  JSON 文本
     * @param type  目标类型
     * @param <T>   目标类型参数
     * @return 反序列化结果，文本为空返回 null
     */
    public static <T> T parse(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 解析失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * JSON 数组文本转列表。
     *
     * @param json      JSON 文本
     * @param reference 类型引用
     * @param <T>       元素类型
     * @return 列表，文本为空返回空列表
     */
    public static <T> List<T> parseList(String json, TypeReference<List<T>> reference) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, reference);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON 列表解析失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * JSON 对象文本转映射。
     *
     * @param json JSON 文本
     * @return 有序映射，非对象文本返回空映射
     */
    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (!node.isObject()) {
                return new LinkedHashMap<>();
            }
            return MAPPER.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 任意 JSON 文本转通用结构。
     *
     * @param json JSON 文本
     * @return 映射、列表或标量，解析失败返回 null
     */
    public static Object parseAny(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 取 JSON 树。
     *
     * @param json JSON 文本
     * @return JSON 节点，解析失败返回 null
     */
    public static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 按点号下标路径取值，兼容 {@code $.a.b[0]} 与 {@code a.b} 两种写法。
     *
     * <p>本方法是抽取节点 {@code JSON} 与 {@code JSONPATH} 两种解析器的共同实现口径，
     * 只覆盖 JSONPath 的一个 <b>子集</b>，不支持的部分不假装支持：</p>
     *
     * <ul>
     *   <li>支持：{@code $} 根前缀（可省略）、点号取属性 {@code a.b}、数字下标 {@code a[0]}、
     *       连续下标 {@code a[0][1]}、前导点 {@code .a.b}</li>
     *   <li>不支持：通配 {@code [*]}、递归下降 {@code ..}、过滤器 {@code [?(...)]}、
     *       切片 {@code [0:2]}、并集 {@code ['a','b']}、负下标，以及函数与脚本表达式；
     *       遇到这些写法只会按字面段解析并大多返回 null</li>
     * </ul>
     *
     * @param root       JSON 结构（映射、列表或标量）
     * @param expression 路径
     * @return 路径值，未命中返回 null
     */
    public static Object path(Object root, String expression) {
        Object current = root;
        if (expression == null || expression.isBlank()) {
            return current;
        }
        String normalized = expression.trim();
        if (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
        }
        for (String segment : normalized.split("\\.")) {
            if (segment.isEmpty()) {
                continue;
            }
            String name = segment;
            String subscripts = "";
            int bracket = segment.indexOf('[');
            if (bracket >= 0) {
                name = segment.substring(0, bracket);
                subscripts = segment.substring(bracket);
            }
            if (!name.isEmpty()) {
                if (!(current instanceof Map)) {
                    return null;
                }
                current = ((Map<?, ?>) current).get(name);
            }
            current = subscript(current, subscripts);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * 连续下标访问。
     *
     * @param current    当前值
     * @param subscripts 形如 {@code [0][2]} 的下标串
     * @return 下标值，越界或类型不符返回 null
     */
    private static Object subscript(Object current, String subscripts) {
        if (subscripts == null || subscripts.isEmpty()) {
            return current;
        }
        Matcher matcher = SUBSCRIPT.matcher(subscripts);
        while (matcher.find()) {
            if (!(current instanceof List)) {
                return null;
            }
            int index = Integer.parseInt(matcher.group(1));
            List<?> list = (List<?>) current;
            current = index < list.size() ? list.get(index) : null;
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
