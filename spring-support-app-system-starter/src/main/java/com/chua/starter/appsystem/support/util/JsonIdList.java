package com.chua.starter.appsystem.support.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ID 集合与 JSON 字符串互化工具
 * <p>
 * 主机池与项目主机选择都以 JSON 数组字符串（如 {@code [1,2,3]}）落库。历史数据里存在
 * {@code null}、空串、{@code []}、以及混入字符串元素 {@code ["1"]} 四种写法，
 * 统一在此收口解析，避免各处各写一份容错。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class JsonIdList {

    /**
     * Jackson
    */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 解析 JSON 数组字符串为主机 标识 列表（保序去重，非法元素跳过）
     *
     * @param json JSON 数组字符串，可为空
     * @return 标识 列表，永不为 null
     */
    public static List<Long> parse(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        List<Object> raw;
        try {
            raw = MAPPER.readValue(json, new TypeReference<List<Object>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Object item : raw) {
            Long id = toLong(item);
            if (id != null) {
                ids.add(id);
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 序列化为 JSON 数组字符串；空集合写回 null，避免库里留一堆 {@code []}
     *
     * @param ids 标识 列表，可为空
     * @return JSON 字符串或 null
     */
    public static String toJson(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(ids);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 宽松取 标识：数字、数字字符串都能吃
     *
     * @param item 元素
     * @return 标识，取不到时为 null
     */
    private static Long toLong(Object item) {
        if (item instanceof Number number) {
            return number.longValue();
        }
        if (item instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 工具类禁止实例化
     */
    private JsonIdList() {
    }
}
