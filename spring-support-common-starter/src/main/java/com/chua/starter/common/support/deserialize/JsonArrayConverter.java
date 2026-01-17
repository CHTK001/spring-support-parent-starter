package com.chua.starter.common.support.deserialize;

import com.chua.common.support.function.Joiner;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;

/**
 * 数组解析
 *
 * @author CH
 */
public class JsonArrayConverter implements Converter<Object[], String> {
    @Override
    public String convert(Object[] value) {
        return Joiner.on(",").join(value);
    }

    @Override
    public JavaType getInputType(TypeFactory typeFactory) {
        return typeFactory.constructArrayType(Object.class);
    }

    @Override
    public JavaType getOutputType(TypeFactory typeFactory) {
        return typeFactory.constructType(String.class);
    }
}

