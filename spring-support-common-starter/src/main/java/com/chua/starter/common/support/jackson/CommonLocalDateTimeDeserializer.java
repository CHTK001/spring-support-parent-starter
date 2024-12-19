package com.chua.starter.common.support.jackson;

import com.chua.common.support.lang.date.DateUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;

import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDateTime;

/**
 * 通用LocalDateTime解析
 * @author CH
 * @since 2024/12/19
 */
public class CommonLocalDateTimeDeserializer extends LocalDateTimeDeserializer {

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        IOException ex;
        try {
            return super.deserialize(parser, context);
        } catch (IOException e) {
            ex = e;
        }

        if(parser.hasTokenId(JsonTokenId.ID_STRING)) {
            try {
                return DateUtils.toLocalDateTime(parser.getText());
            } catch (Exception ignored) {
            }
        }
        throw ex;
    }
}
