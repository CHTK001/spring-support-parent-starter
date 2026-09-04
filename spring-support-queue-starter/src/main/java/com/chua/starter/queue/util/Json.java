package com.chua.starter.queue.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 序列化/反序列化工具
 *
 * @author CH
 */
public class Json {

    /** Jackson ObjectMapper 实例 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 将对象序列化为 JSON 字符串
     *
     * @param value 待序列化对象
     * @return JSON 字符串
     */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialize failure", e);
        }
    }

    /**
     * 从 JSON 字符串反序列化为指定类型的对象
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   泛型
     * @return 反序列化后的对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("JSON parse failure", e);
        }
    }

    /**
     * 从 JSON 字符串反序列化为 Map
     *
     * @param content JSON 字符串
     * @return Map 对象
     */
    public static java.util.Map<String, Object> getJsonObject(String content) {
        try {
            return MAPPER.readValue(content, new TypeReference<java.util.Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("JSON parse failure", e);
        }
    }
}
