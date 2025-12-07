package com.chua.starter.common.support.serialize;

import com.chua.common.support.json.Json;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * @author CH
 */
public class JsonArrayToStringSerialize extends JsonSerializer<Object[]> {
    @Override
    public void serialize(Object[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(Json.toJson(value));
    }
}

