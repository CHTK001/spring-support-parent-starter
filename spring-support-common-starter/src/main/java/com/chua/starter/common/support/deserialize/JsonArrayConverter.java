package com.chua.starter.common.support.deserialize;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;

/**
 * 数组解析
 *
 * @author CH
 * @since 4.0.0
 */
public class JsonArrayConverter implements Converter<Object[], String> {
    @Override
    /** 转换 */
    public String convert(Object[] value) {
        return java.util.Arrays.stream(value).map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    @Override
    /** 获取输入类型 */
    public JavaType getInputType(TypeFactory typeFactory) {
        return typeFactory.constructArrayType(Object.class);
    }

    @Override
    /** 获取输出类型 */
    public JavaType getOutputType(TypeFactory typeFactory) {
        return typeFactory.constructType(String.class);
    }
}
