package com.chua.starter.common.support.jackson.configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import com.chua.common.support.collection.GuavaHashBasedTable;
import com.chua.common.support.collection.Table;
import com.chua.common.support.lang.date.DateTime;
import com.chua.starter.common.support.jackson.*;
import com.chua.starter.common.support.jackson.handler.JacksonProblemHandler;
import com.chua.starter.common.support.jackson.handler.JsonArray2StringJacksonProblemHandler;
import com.chua.starter.common.support.jackson.handler.JsonObject2ArrayListJacksonProblemHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.JsonMixinModule;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

/**
 * Jackson配置
 *
 * @author CH
 * @since 2024/7/19
 */
@EnableConfigurationProperties(JacksonProperties.class)
public class JacksonConfiguration {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JacksonConfiguration.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static ObjectMapper createObjectMapper(boolean forEverything) {
        return createObjectMapper(forEverything, false);
    }

    public static ObjectMapper createObjectMapper(boolean forEverything, boolean includeNull) {
        log.info("[Jackson配置]开始创建 ObjectMapper, forEverything={}, includeNull={}", forEverything, includeNull);
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
        log.info("[Jackson配置]ObjectMapper 基础类: {}", objectMapper.getClass().getName());
        if(includeNull) {
            objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
            log.debug("[Jackson配置]属性包含策略: ALWAYS (包含null值)");
        } else {
            objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
            log.debug("[Jackson配置]属性包含策略: NON_NULL (排除null值)");
        }
        if(forEverything) {
            StdTypeResolverBuilder typer = TypeResolverBuilder.forEverything(objectMapper).init(JsonTypeInfo.Id.CLASS, null)
                    .inclusion(JsonTypeInfo.As.PROPERTY);
            objectMapper.setDefaultTyping(typer);
            log.info("[Jackson配置]启用全类型序列化 (forEverything=true)");
        }
        objectMapper.setDateFormat(SIMPLE_DATE_FORMAT);
        log.debug("[Jackson配置]日期格式: {}", SIMPLE_DATE_FORMAT.toPattern());
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER));
        javaTimeModule.addSerializer(LocalTime.class, new  LocalTimeSerializer(TIME_FORMATTER));
        log.debug("[Jackson配置]注册时间序列化器: LocalDateTime={}, LocalDate={}, LocalTime={}", 
                LocalDateTimeSerializer.class.getName(), 
                LocalDateSerializer.class.getName(), 
                LocalTimeSerializer.class.getName());

        javaTimeModule.addDeserializer(LocalDateTime.class, new CommonLocalDateTimeDeserializer());
        javaTimeModule.addDeserializer(LocalDate.class, new CommonLocalDateDeserializer());
        javaTimeModule.addDeserializer(LocalTime.class, new CommonLocalTimeDeserializer());
        javaTimeModule.addDeserializer(Year.class, new CommonYearDeserializer());
        javaTimeModule.addDeserializer(DateTime.class, new DateTimeDeserializer());
        log.debug("[Jackson配置]注册时间反序列化器: LocalDateTime={}, LocalDate={}, LocalTime={}, Year={}, DateTime={}", 
                CommonLocalDateTimeDeserializer.class.getName(),
                CommonLocalDateDeserializer.class.getName(),
                CommonLocalTimeDeserializer.class.getName(),
                CommonYearDeserializer.class.getName(),
                DateTimeDeserializer.class.getName());

        javaTimeModule.addSerializer(Date.class, new DateSerializer(true, SIMPLE_DATE_FORMAT));
        log.debug("[Jackson配置]注册 Date 序列化器: {}", DateSerializer.class.getName());
        objectMapper.setTimeZone(TimeZone.getDefault());
        log.debug("[Jackson配置]时区: {}", TimeZone.getDefault().getID());
//        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        objectMapper.registerModule(new SimpleModule().addSerializer(new NullValueSerializer(null)));
        log.debug("[Jackson配置]注册 NullValueSerializer: {}", NullValueSerializer.class.getName());

        objectMapper.registerModule(javaTimeModule);
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JsonMixinModule());
        objectMapper.registerModule(new ParameterNamesModule());
        log.info("[Jackson配置]注册模块: JavaTimeModule={}, Jdk8Module={}, JsonMixinModule={}, ParameterNamesModule={}", 
                JavaTimeModule.class.getName(),
                Jdk8Module.class.getName(),
                JsonMixinModule.class.getName(),
                ParameterNamesModule.class.getName());
        log.info("[Jackson配置]ObjectMapper 创建完成, 类型: {}", objectMapper.getClass().getName());
        return objectMapper;
    }
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper defaultObjectMapper(JacksonProperties jacksonProperties) {
        log.info("[Jackson配置]创建默认 ObjectMapper Bean, includeNull={}", jacksonProperties.isIncludeNull());
        ObjectMapper objectMapper = createObjectMapper(false, jacksonProperties.isIncludeNull());
        log.info("[Jackson配置]默认 ObjectMapper Bean 创建完成, 类型: {}", objectMapper.getClass().getName());
        return objectMapper;
    }
    @Bean
    @ConditionalOnMissingBean
    public Jackson2ObjectMapperBuilder objectMapperBuilder(ObjectMapper objectMapper) {
        log.info("[Jackson配置]创建 Jackson2ObjectMapperBuilder, 使用 ObjectMapper: {}", objectMapper.getClass().getName());
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        builder.configure(objectMapper);
        log.debug("[Jackson配置]Jackson2ObjectMapperBuilder 配置完成");
        return builder;
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
            NULLABLE_FIELDS.put("START_OBJECT", ArrayList.class, JsonObject2ArrayListJacksonProblemHandler.class);
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
