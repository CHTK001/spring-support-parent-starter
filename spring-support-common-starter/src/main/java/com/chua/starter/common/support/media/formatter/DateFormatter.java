package com.chua.starter.common.support.media.formatter;

import org.springframework.format.annotation.DateTimeFormat;

import com.chua.common.support.spi.annotations.Spi;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
* 日期格式化器
* <p>
* 用于将日期类型的值按照指定格式进行格式化输出。
* 支持 日期、本地日期、本地日期时间、本地时间 等类型。
* </p>
*
* @版本 1.0.0
* @see Formatter
* @author CH
* @since 4.0.0
 */
@Spi({"date", "localdate", "LocalDateTime", "LocalTime"})
public class DateFormatter implements Formatter {
    @Override
    /** 格式化 */
    public Object format(Object value, Field field) {
        DateTimeFormat dateTimeFormat = field.getDeclaredAnnotation(DateTimeFormat.class);
        if (null != dateTimeFormat) {
            String pattern = dateTimeFormat.pattern();
            try {
                if (value instanceof LocalDateTime) {
                    return ((LocalDateTime) value).format(DateTimeFormatter.ofPattern(pattern));
                } else if (value instanceof java.time.LocalDate) {
                    return ((java.time.LocalDate) value).format(DateTimeFormatter.ofPattern(pattern));
                } else if (value instanceof java.time.LocalTime) {
                    return ((java.time.LocalTime) value).format(DateTimeFormatter.ofPattern(pattern));
                } else if (value instanceof java.util.Date) {
                    return new java.text.SimpleDateFormat(pattern).format((java.util.Date) value);
                }
            } catch (Exception ignored) {
            }
        }
        return value;
    }
}
