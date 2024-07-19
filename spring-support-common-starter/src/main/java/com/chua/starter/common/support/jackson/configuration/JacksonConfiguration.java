package com.chua.starter.common.support.jackson.configuration;


import com.chua.common.support.collection.GuavaHashBasedTable;
import com.chua.common.support.collection.Table;
import com.chua.starter.common.support.jackson.handler.JacksonProblemHandler;
import com.chua.starter.common.support.jackson.handler.JsonArray2StringJacksonProblemHandler;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

/**
 * Jackson配置
 * @author CH
 * @since 2024/7/19
 */
public class JacksonConfiguration  {


    @Bean
    ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // jackson 1.9 and before
//        objectMapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // or jackson 2.0
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.addHandler(new NullableFieldsDeserializationProblemHandler());
        return objectMapper;
    }

    static class NullableFieldsDeserializationProblemHandler extends DeserializationProblemHandler {

        static Table<String, Class<?>, Class<? extends JacksonProblemHandler>> NULLABLE_FIELDS = new GuavaHashBasedTable<>();
        static {
            NULLABLE_FIELDS.put("START_ARRAY", String.class, JsonArray2StringJacksonProblemHandler.class);
        }

        @Override
        public Object handleUnexpectedToken(DeserializationContext ctxt, JavaType targetType, JsonToken t, JsonParser p, String failureMsg) throws IOException {
            Class<?> targetRawClass = targetType.getRawClass();
            String name = t.name();
            try {
                return NULLABLE_FIELDS.get(name, targetRawClass).getDeclaredConstructor().newInstance().handle(ctxt, targetType, t, p, failureMsg);
            } catch (Exception ignored) {
            }
            return super.handleUnexpectedToken(ctxt, targetType, t, p, failureMsg);
        }
    }
}