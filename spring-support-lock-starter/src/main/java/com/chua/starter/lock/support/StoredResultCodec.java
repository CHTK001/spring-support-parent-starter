package com.chua.starter.lock.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Redis 幂等结果编解码器。
 *
 * @author CH
 * @since 2026-03-28
 */
public class StoredResultCodec {

    private final ObjectMapper objectMapper;

    public StoredResultCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(StoredResult.from(value, objectMapper));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化幂等结果失败", ex);
        }
    }

    public Object deserialize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            StoredResult storedResult = objectMapper.readValue(value, StoredResult.class);
            if (storedResult.isNullValue()) {
                return NullValue.INSTANCE;
            }
            if (storedResult.getPayload() == null) {
                return null;
            }
            if (!StringUtils.hasText(storedResult.getType())) {
                return objectMapper.treeToValue(storedResult.getPayload(), Object.class);
            }

            Class<?> targetType = ClassUtils.forName(storedResult.getType(), Thread.currentThread().getContextClassLoader());
            return objectMapper.treeToValue(storedResult.getPayload(), targetType);
        } catch (Exception ex) {
            throw new IllegalStateException("反序列化幂等结果失败", ex);
        }
    }

    public static class StoredResult {
        private boolean nullValue;
        private String type;
        private JsonNode payload;

        public static StoredResult from(Object value, ObjectMapper objectMapper) {
            StoredResult storedResult = new StoredResult();
            if (value == null || value == NullValue.INSTANCE) {
                storedResult.setNullValue(true);
                return storedResult;
            }

            storedResult.setType(value.getClass().getName());
            storedResult.setPayload(objectMapper.valueToTree(value));
            return storedResult;
        }

        public boolean isNullValue() {
            return nullValue;
        }

        public void setNullValue(boolean nullValue) {
            this.nullValue = nullValue;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public JsonNode getPayload() {
            return payload;
        }

        public void setPayload(JsonNode payload) {
            this.payload = payload;
        }
    }
}
