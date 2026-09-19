package com.chua.starter.common.support.jackson;


import com.chua.common.support.utils.DateUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;

import java.io.IOException;
import java.time.LocalDate;

/**
 * 通用本地日期解析
 * @since 2024/12/19
 * @author CH
 */
public class CommonLocalDateDeserializer extends LocalDateDeserializer {

    @Override
    /** 反序列化 */
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        IOException ex;
        try {
            return super.deserialize(parser, context);
        } catch (IOException e) {
            ex = e;
        }

        if(parser.hasTokenId(JsonTokenId.ID_STRING)) {
            try {
                return DateUtils.toLocalDate(parser.getText());
            } catch (Exception ignored) {
            }
        }
        throw ex;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt,
                                                BeanProperty property) throws JsonMappingException {
        return this;
    }
}

