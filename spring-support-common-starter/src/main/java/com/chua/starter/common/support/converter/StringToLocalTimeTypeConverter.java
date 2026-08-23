package com.chua.starter.common.support.converter;

import com.chua.common.support.utils.DateUtils;
import org.springframework.core.convert.converter.Converter;

import java.time.LocalTime;

/**
 * str -> LocalTime
 *
 * @since 2022/8/11 9:14
 * @author CH
 */
public class StringToLocalTimeTypeConverter implements Converter<String, LocalTime> {
    @Override
    /** 转换 */
    public LocalTime convert(String source) {
        try {
        return DateUtils.toLocalTime(source);
        } catch (Exception e) {
            return null;
        }
    }
}

