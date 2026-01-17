package com.chua.starter.common.support.converter;

import com.chua.common.support.time.date.DateUtils;
import org.springframework.core.convert.converter.Converter;

import java.text.ParseException;
import java.util.Date;

/**
 * str -> date
 *
 * @author CH
 * @since 2022/8/11 9:14
 */
public class StringToDateTypeConverter implements Converter<String, Date> {
    @Override
    public Date convert(String source) {
        try {
            return DateUtils.parseDate(source);
        } catch (ParseException e) {
            return null;
        }
    }

}

