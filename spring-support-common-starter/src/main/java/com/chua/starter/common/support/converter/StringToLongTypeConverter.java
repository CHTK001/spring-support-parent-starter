package com.chua.starter.common.support.converter;

import com.chua.common.support.time.date.DateUtils;
import com.chua.common.support.core.utils.NumberUtils;
import org.springframework.core.convert.converter.Converter;

import java.text.ParseException;
import java.util.Date;

/**
 * str -> Long
 * @author CH
 * @since 2024/9/24
 */
public class StringToLongTypeConverter implements Converter<String, Long> {
    @Override
    public Long convert(String source) {
        if(NumberUtils.isNumber(source)) {
            return NumberUtils.toLong(source);
        }

        try {
            Date date = DateUtils.parseDate(source);
            return date.getTime();
        } catch (ParseException ignored) {
        }

        return null;
    }
}

