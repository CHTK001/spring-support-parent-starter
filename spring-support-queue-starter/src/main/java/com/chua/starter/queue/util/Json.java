package com.chua.starter.queue.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON工具类
 *
 * @author CH
 * @since 2025-12-25
 */
public class Json {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * 对象转JSON字符串
     *
     * @param obj 对象
     * @return JSON字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON序列化失败: " + obj.getClass().getName(), e);
        }
    }

    /**
     * JSON字符串转对象
     *
     * @param json  JSON字符串
     * @param clazz 目标类型
     * @param <T>   类型参数
     * @return 对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("JSON反序列化失败: " + clazz.getName(), e);
        }
    }
}

