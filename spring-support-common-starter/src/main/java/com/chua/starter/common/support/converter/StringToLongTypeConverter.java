package com.chua.starter.common.support.converter;

import com.chua.common.support.utils.DateUtils;
import com.chua.common.support.utils.NumberUtils;
import org.springframework.core.convert.converter.Converter;

import java.util.Date;

/**
 * str -> Long
 * @since 2024/9/24
 * @author CH
 */
public class StringToLongTypeConverter implements Converter<String, Long> {
    @Override
    public Long convert(String source) {
        if(NumberUtils.isNumber(source)) {
            return NumberUtils.toLong(source);
        }

        Date date = DateUtils.parseDate(source);
        if (date != null) {
            return date.getTime();
        }

        return null;
    }
}
