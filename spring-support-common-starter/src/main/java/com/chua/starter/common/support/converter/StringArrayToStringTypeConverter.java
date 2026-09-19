package com.chua.starter.common.support.converter;

import org.springframework.core.convert.converter.Converter;

/**
 * str -> 日期
 *
 * @since 2022/8/11 9:14
 * @author CH
 */
public class StringArrayToStringTypeConverter implements Converter<String[], String> {
    @Override
    /**
     * 转换
    */
    public String convert(String[] source) {
        return java.lang.String.join(",", source);
    }

}
