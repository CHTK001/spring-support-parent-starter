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
 * @author CH
 * @since 2024/8/24
 */
public class NullValueSerializer extends StdSerializer<NullValue> {

    @Serial
    private static final long serialVersionUID = 1999052150548658808L;

    private final String classIdentifier;

    /**
     * @param classIdentifier can be {@literal null} and will be defaulted to {@code @class}.
     */
    public NullValueSerializer(@Nullable String classIdentifier) {

        super(NullValue.class);
        this.classIdentifier = StringUtils.hasText(classIdentifier) ? classIdentifier : "@class";
    }

    @Override
    public void serialize(NullValue value, JsonGenerator jsonGenerator, SerializerProvider provider)
            throws IOException {

        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField(classIdentifier, NullValue.class.getName());
        jsonGenerator.writeEndObject();
    }

    @Override
    public void serializeWithType(NullValue value, JsonGenerator jsonGenerator, SerializerProvider serializers,
                                  TypeSerializer typeSerializer) throws IOException {

        serialize(value, jsonGenerator, serializers);
    }
}