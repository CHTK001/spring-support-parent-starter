package com.chua.starter.common.support.converter;

import com.chua.common.support.utils.DateUtils;
import org.springframework.core.convert.converter.Converter;

import java.time.LocalDate;

/**
 * str -> LocalDate
 *
 * @since 2022/8/11 9:14
 * @author CH
 */
public class StringToLocalDateTypeConverter implements Converter<String, LocalDate> {
    @Override
    public LocalDate convert(String source) {
        return DateUtils.toLocalDate(source);
    }
}

