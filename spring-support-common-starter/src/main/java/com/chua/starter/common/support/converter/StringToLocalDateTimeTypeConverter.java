package com.chua.starter.common.support.converter;

import com.chua.common.support.utils.DateUtils;
import org.springframework.core.convert.converter.Converter;

import java.time.LocalDateTime;

/**
 * str -> 本地日期时间
 *
 * @since 2022/8/11 9:14
 * @author CH
 */
public class StringToLocalDateTimeTypeConverter implements Converter<String, LocalDateTime> {
    @Override
    /**
     * 转换
     * @param source 字符串
     * @return 返回值
    */
    public LocalDateTime convert(String source) {
        try {
        return DateUtils.toLocalDateTime(source);
        } catch (Exception e) {
            return null;
        }
    }
}

