package com.chua.starter.soft.support.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SoftJsons {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {
    };

    private SoftJsons() {
    }

    public static ObjectMapper mapper() {
        return OBJECT_MAPPER;
    }

    public static JsonNode readTree(String json) throws Exception {
        return OBJECT_MAPPER.readTree(json);
    }

    public static List<String> toStringList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST_TYPE);
        } catch (Exception ignored) {
            return Collections.singletonList(json);
        }
    }

    public static Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    public static List<Map<String, Object>> toMapList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json, MAP_LIST_TYPE);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    public static String toJson(List<String> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values == null ? Collections.emptyList() : values);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 JSON 失败", e);
        }
    }

    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 JSON 失败", e);
        }
    }
}
