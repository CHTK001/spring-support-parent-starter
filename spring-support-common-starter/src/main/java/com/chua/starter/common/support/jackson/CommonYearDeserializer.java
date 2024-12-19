package com.chua.starter.common.support.jackson;

import com.chua.common.support.lang.date.DateUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.datatype.jsr310.deser.YearDeserializer;

import java.io.IOException;
import java.text.ParseException;
import java.time.Year;

/**
 * Year反序列化
 *
 * @author CH
 * @since 2024/12/19
 */
public class CommonYearDeserializer extends YearDeserializer {
    @Override
    public Year deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken t = parser.currentToken();
        if (t == JsonToken.VALUE_STRING) {
            try {
                return _fromString(parser, context, parser.getText());
            } catch (IOException e) {
                try {
                    return Year.from(DateUtils.toLocalDateTime(parser.getText()));
                } catch (ParseException ex) {
                    throw e;
                }
            }
        }
        // 30-Sep-2020, tatu: New! "Scalar from Object" (mostly for XML)
        if (t == JsonToken.START_OBJECT) {
            return _fromString(parser, context,
                    context.extractScalarFromObject(parser, this, handledType()));
        }
        if (t == JsonToken.VALUE_NUMBER_INT) {
            return _fromNumber(context, parser.getIntValue());
        }
        if (t == JsonToken.VALUE_EMBEDDED_OBJECT) {
            return (Year) parser.getEmbeddedObject();
        }
        if (parser.hasToken(JsonToken.START_ARRAY)) {
            return _deserializeFromArray(parser, context);
        }
        return _handleUnexpectedToken(context, parser, JsonToken.VALUE_STRING, JsonToken.VALUE_NUMBER_INT);
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt,
                                                BeanProperty property) throws JsonMappingException {
        return this;
    }
}
