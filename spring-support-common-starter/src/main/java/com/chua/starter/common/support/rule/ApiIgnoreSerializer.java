package com.chua.starter.common.support.rule;

import com.chua.starter.common.support.annotations.ApiIgnore;
import com.chua.starter.common.support.annotations.PrivacyEncrypt;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;
import java.util.Objects;

/**
 * 忽略字段
 * @author CH
 * @since 2025/1/1
 * @see com.chua.starter.common.support.annotations.ApiIgnore
 * @see com.chua.starter.common.support.annotations.ApiGroup
 */
public class ApiIgnoreSerializer extends JsonSerializer<String> implements ContextualSerializer {
    private final Class<?>[] group;

    public ApiIgnoreSerializer(Class<?>[] group) {
        this.group = group;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        System.out.println();
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider serializerProvider, BeanProperty beanProperty) throws JsonMappingException {
        if (beanProperty != null) {
            if (Objects.equals(beanProperty.getType().getRawClass(), String.class)) {
                ApiIgnore apiIgnore = beanProperty.getAnnotation(ApiIgnore.class);
                if (apiIgnore == null) {
                    apiIgnore = beanProperty.getContextAnnotation(ApiIgnore.class);
                }
                if (apiIgnore != null) {
                    return new ApiIgnoreSerializer(apiIgnore.value());
                }
            }
            return serializerProvider.findValueSerializer(beanProperty.getType(), beanProperty);
        }
        return serializerProvider.findNullValueSerializer(null);
    }
}
