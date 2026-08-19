package com.chua.common.support.lang.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * JSON 工具类，提供 JSON 序列化与反序列化功能
 *
 * @author CH
 */
public class Json {

    /**
     * 全局共享的 Jackson ObjectMapper 实例，用于 JSON 序列化与反序列化
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 将 JSON 字符串解析为 Map 对象
     *
     * @param json JSON 字符串
     * @return 解析后的 Map 对象
     * @throws RuntimeException 如果 JSON 解析失败
     */
    public static Map<String, Object> getJsonObject(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将 Java 对象序列化为 JSON 字符串
     *
     * @param obj 待序列化的 Java 对象
     * @return JSON 字符串，序列化失败时返回 "{}"
     */
    public static String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * 将 Java 对象序列化为 JSON 字节数组
     *
     * @param obj 待序列化的 Java 对象
     * @return JSON 字节数组，序列化失败时返回 "{}" 的字节数组
     */
    public static byte[] toJSONBytes(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            return "{}".getBytes();
        }
    }

    /**
     * 将 Java 对象格式化为美化的 JSON 字符串（带缩进和换行）
     *
     * @param obj 待格式化的 Java 对象
     * @return 美化后的 JSON 字符串，格式化失败时返回 "{}"
     */
    public static String prettyFormat(Object obj) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
