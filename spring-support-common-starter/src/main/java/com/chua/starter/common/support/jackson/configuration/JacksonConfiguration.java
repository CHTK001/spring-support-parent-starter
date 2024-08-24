package com.chua.starter.common.support.jackson.configuration;


import com.chua.common.support.collection.GuavaHashBasedTable;
import com.chua.common.support.collection.Table;
import com.chua.starter.common.support.jackson.NullValueSerializer;
import com.chua.starter.common.support.jackson.TypeResolverBuilder;
import com.chua.starter.common.support.jackson.handler.JacksonProblemHandler;
import com.chua.starter.common.support.jackson.handler.JsonArray2StringJacksonProblemHandler;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.DateSerializer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.boot.jackson.JsonMixinModule;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;

/**
 * Jackson配置
 *
 * @author CH
 * @since 2024/7/19
 */
public class JacksonConfiguration {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    public static ObjectMapper createObjectMapper(boolean forEverything ) {
        ObjectMapper objectMapper = JsonMapper.builder()
                // 反序列化时对字段名的大小写不敏感
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                // 不存在的字段时，不会抛出异常
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // 当找不解析类型（含子类），不会抛异常，继续尝试反序列化
                .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                // 忽略序列化和反序列化的大小写
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                // 当找不到类型,会序列化成空对象
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false)
                .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false)
                .configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true)
                .addHandler(new NullableFieldsDeserializationProblemHandler())
                .build();
        if(forEverything) {
            StdTypeResolverBuilder typer = TypeResolverBuilder.forEverything(objectMapper).init(JsonTypeInfo.Id.CLASS, null)
                    .inclusion(JsonTypeInfo.As.PROPERTY);
            objectMapper.setDefaultTyping(typer);
        }
        objectMapper.setDateFormat(SIMPLE_DATE_FORMAT);
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER));
        javaTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(TIME_FORMATTER));
        javaTimeModule.addSerializer(Date.class, new DateSerializer(true, SIMPLE_DATE_FORMAT));
        objectMapper.setTimeZone(TimeZone.getDefault());
//        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        objectMapper.registerModule(new SimpleModule().addSerializer(new NullValueSerializer(null)));

        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(javaTimeModule);
        objectMapper.registerModule(new JsonMixinModule());
        objectMapper.registerModule(new ParameterNamesModule());
        return objectMapper;
    }
    @Bean
    ObjectMapper objectMapper() {
        return createObjectMapper(false);
    }

    static class TransientFieldIgnoringDeserializer extends StdDeserializer<Object> {
        private static final long serialVersionUID = 1L;

        public TransientFieldIgnoringDeserializer() {
            super(Object.class);
        }

        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return p.readValuesAs(Object.class);
        }
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