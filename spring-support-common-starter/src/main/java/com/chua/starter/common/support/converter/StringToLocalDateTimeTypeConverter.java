package com.chua.starter.common.support.converter;

import com.chua.common.support.utils.DateUtils;
import org.springframework.core.convert.converter.Converter;

import java.time.LocalDateTime;

/**
 * str -> LocalDateTime
 *
 * @since 2022/8/11 9:14
 * @author CH
 */
public class StringToLocalDateTimeTypeConverter implements Converter<String, LocalDateTime> {
    @Override
    public LocalDateTime convert(String source) {
        return DateUtils.toLocalDateTime(source);
    }
}

