package com.chua.starter.common.support.jackson;

import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.lang.date.DateTimeParser;
import com.chua.common.support.lang.date.DateUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.datatype.jsr310.deser.JSR310DateTimeDeserializerBase;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 通用LocalTime解析
 * @author CH
 * @since 2024/12/19
 */
public class DateTimeDeserializer extends JSR310DateTimeDeserializerBase<DateTime> {

    public DateTimeDeserializer () {
        super(DateTime.class, DateTimeFormatter.ISO_LOCAL_TIME);
    }
    protected DateTimeDeserializer(JSR310DateTimeDeserializerBase<DateTime> base, DateTimeFormatter f) {
        super(base, f);
    }

    protected DateTimeDeserializer(JSR310DateTimeDeserializerBase<DateTime> base, Boolean leniency) {
        super(base, leniency);
    }

    protected DateTimeDeserializer(JSR310DateTimeDeserializerBase<DateTime> base, JsonFormat.Shape shape) {
        super(base, shape);
    }

    protected DateTimeDeserializer(Class<DateTime> supportedType, DateTimeFormatter f) {
        super(supportedType, f);
    }

    public DateTimeDeserializer(Class<DateTime> supportedType, DateTimeFormatter f, Boolean leniency) {
        super(supportedType, f, leniency);
    }



    @Override
    public DateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String text = parser.getText();
        try {
            return DateTime.of(text);
        } catch (Exception e) {
            DateTimeParser dateTimeParser = new DateTimeParser(text);
            try {
                return dateTimeParser.parse();
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    @Override
    protected JSR310DateTimeDeserializerBase<DateTime> withDateFormat(DateTimeFormatter dtf) {
        return this;
    }

    @Override
    protected JSR310DateTimeDeserializerBase<DateTime> withLeniency(Boolean leniency) {
        return this;
    }

    @Override
    protected JSR310DateTimeDeserializerBase<DateTime> withShape(JsonFormat.Shape shape) {
        return this;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt,
                                                BeanProperty property) throws JsonMappingException {
        return this;
    }
}

