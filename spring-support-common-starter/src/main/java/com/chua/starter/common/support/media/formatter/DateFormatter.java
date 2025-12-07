package com.chua.starter.common.support.media.formatter;

import com.chua.common.support.annotations.Spi;
import com.chua.common.support.lang.date.DateTime;
import org.springframework.format.annotation.DateTimeFormat;

import java.lang.reflect.Field;

/**
 * 格式�?
 *
 * @author CH
 */
@Spi({"date", "localdate", "LocalDateTime", "LocalTime"})
public class DateFormatter implements Formatter {
    @Override
    public Object format(Object value, Field field) {
        DateTimeFormat dateTimeFormat = field.getDeclaredAnnotation(DateTimeFormat.class);
        if (null != dateTimeFormat) {
            String pattern = dateTimeFormat.pattern();
            try {
                return DateTime.of(value).toString(pattern);
            } catch (Exception ignored) {
            }
        }
        return value;
    }
}

