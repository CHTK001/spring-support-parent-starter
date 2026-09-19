package com.chua.starter.common.support.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.cache.support.NullValue;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.Serial;

/**
 * @since 2024/8/24
 * @author CH
 */
public class NullValueSerializer extends StdSerializer<NullValue> {

    @Serial
    /**
     * 串行版本UID
    */
    private static final long serialVersionUID = 1999052150548658808L;

    /**
     * 类标识符
    */
    private final String classIdentifier;

    /**
     * @param classIdentifier 能否 be {@literal null} 和 will be 默认 转为 {@code @class}.
     */
    public NullValueSerializer(@Nullable String classIdentifier) {

        /**
         * 父。
         * @param NullValue.class 空值.类
         */
        super(NullValue.class);
        this.classIdentifier = StringUtils.hasText(classIdentifier) ? classIdentifier : "@class";
    }

    @Override
    /**
     * 序列化
    */
    public void serialize(NullValue value, JsonGenerator jsonGenerator, SerializerProvider provider)
            throws IOException {

        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField(classIdentifier, NullValue.class.getName());
        jsonGenerator.writeEndObject();
    }

    @Override
    /**
     * 序列化设置类型
     * @param value 值
     * @param jsonGenerator json生成器
     * @param serializers 序列化器
     * @param typeSerializer 类型序列化器
     */
    public void serializeWithType(NullValue value, JsonGenerator jsonGenerator, SerializerProvider serializers,
                                  TypeSerializer typeSerializer) throws IOException {

        /**
         * serialize。
         * @param value 值
         * @param jsonGenerator json生成器
         * @param serializers 序列化器
         */
        serialize(value, jsonGenerator, serializers);
    }
}
