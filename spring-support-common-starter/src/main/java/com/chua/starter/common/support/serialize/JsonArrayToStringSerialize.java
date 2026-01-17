package com.chua.starter.common.support.serialize;
import com.chua.common.support.text.json.Json;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

/**
 * JSON 数组转字符串序列化器
 *
 * @author CH
 */
@Slf4j
public class JsonArrayToStringSerialize extends JsonSerializer<Object[]> {
        @Override
    public void serialize(Object[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("[序列化]使用 JsonArrayToStringSerialize 序列化数组, 长度: {}", value != null ? value.length : 0);
        }
        gen.writeString(Json.toJson(value));
    }
}

