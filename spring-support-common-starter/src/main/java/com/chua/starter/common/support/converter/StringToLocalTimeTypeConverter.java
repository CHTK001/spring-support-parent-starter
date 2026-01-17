package com.chua.starter.common.support.converter;

import com.chua.common.support.time.date.DateUtils;
import org.springframework.core.convert.converter.Converter;

import java.text.ParseException;
import java.time.LocalTime;

/**
 * str -> LocalTime
 *
 * @author CH
 * @since 2022/8/11 9:14
 */
public class StringToLocalTimeTypeConverter implements Converter<String, LocalTime> {
    @Override
    public LocalTime convert(String source) {
        try {
            return DateUtils.toLocalTime(source);
        } catch (ParseException e) {
            return null;
        }
    }
}

