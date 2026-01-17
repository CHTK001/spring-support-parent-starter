package com.chua.starter.common.support.converter;

import com.chua.common.support.time.date.DateUtils;
import org.springframework.core.convert.converter.Converter;

import java.text.ParseException;
import java.time.LocalDateTime;

/**
 * str -> LocalDateTime
 *
 * @author CH
 * @since 2022/8/11 9:14
 */
public class StringToLocalDateTimeTypeConverter implements Converter<String, LocalDateTime> {
    @Override
    public LocalDateTime convert(String source) {
        try {
            return DateUtils.toLocalDateTime(source);
        } catch (ParseException e) {
            return null;
        }
    }
}

