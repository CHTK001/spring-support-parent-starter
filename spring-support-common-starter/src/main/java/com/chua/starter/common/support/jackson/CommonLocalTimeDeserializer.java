package com.chua.starter.common.support.jackson;

import com.chua.common.support.lang.date.DateUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;

import java.io.IOException;
import java.time.LocalTime;

/**
 * 通用LocalTime解析
 * @author CH
 * @since 2024/12/19
 */
public class CommonLocalTimeDeserializer extends LocalTimeDeserializer {
    @Override
    public LocalTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        IOException ex;
        try {
            return super.deserialize(parser, context);
        } catch (IOException e) {
            ex = e;
        }

        if(parser.hasTokenId(JsonTokenId.ID_STRING)) {
            try {
                return DateUtils.toLocalTime(parser.getText());
            } catch (Exception ignored) {
            }
        }
        throw ex;
    }
}
