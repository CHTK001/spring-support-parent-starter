package com.chua.starter.common.support.converter;

import com.chua.common.support.function.Joiner;
import org.springframework.core.convert.converter.Converter;

/**
 * str -> date
 *
 * @author CH
 * @since 2022/8/11 9:14
 */
public class StringArrayToStringTypeConverter implements Converter<String[], String> {
    @Override
    public String convert(String[] source) {
        return Joiner.on(",").join(source);
    }

}

