package com.chua.starter.common.support.text.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * JSON 序列化/反序列化工具类，统一封装 Jackson ObjectMapper。
 *
 * <p>本类是 spring-support-common-starter 提供的统一 JSON 能力，其他 spring-* 模块必须通过本类复用，
 * 禁止在各模块中重复实现 Json 工具类。</p>
 *
 * @author CH
 */
public final class Json {

    /** Jackson ObjectMapper 单例 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 待序列化对象
     * @return JSON 字符串
     * @throws RuntimeException 当序列化失败时
     */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialize failure", e);
        }
    }

    /**
     * 将对象序列化为 JSON 字符串（与 {@link #toJson(Object)} 等价，提供更明确的命名）。
     *
     * @param value 待序列化对象
     * @return JSON 字符串
     * @throws RuntimeException 当序列化失败时
     */
    public static String toJSONString(Object value) {
        return toJson(value);
    }

    /**
     * 从 JSON 字符串反序列化为指定类型。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   泛型
     * @return 反序列化后的对象
     * @throws RuntimeException 当反序列化失败时
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("JSON parse failure", e);
        }
    }

    /**
     * 从 JSON 字符串反序列化为 Map。
     *
     * @param content JSON 字符串
     * @return Map 对象
     * @throws RuntimeException 当反序列化失败时
     */
    public static Map<String, Object> getJsonObject(String content) {
        try {
            return MAPPER.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("JSON parse failure", e);
        }
    }
}
