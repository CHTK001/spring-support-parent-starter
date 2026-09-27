package com.chua.starter.eduai.collect.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 来源配置 JSON 读取工具。
 *
 * @since 4.0.0.42
 * @author CH
 */
public final class SourceConfigReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SourceConfigReader() {
    }

    /**
     * 读取配置中的字符串字段。
     *
     * @param json 配置 JSON
     * @param key 字段名
     * @return 值（缺失时 null）
     */
    public static String text(String json, String key) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode value = root.get(key);
            return value == null || value.isNull() ? null : value.asText();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 读取配置中的对象/数组字段，返回其 JSON 文本（标量返回原文）。
     * <p>注意：Jackson 的 {@code asText()} 对对象/数组返回空串，嵌套配置必须用本方法。</p>
     *
     * @param json 配置 JSON
     * @param key  字段名
     * @return JSON 文本（缺失时 null）
     */
    public static String json(String json, String key) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode value = root.get(key);
            if (value == null || value.isNull()) {
                return null;
            }
            return value.isObject() || value.isArray() ? value.toString() : value.asText();
        } catch (Exception e) {
            return null;
        }
    }
}
