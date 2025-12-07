package com.chua.starter.common.support.media.formatter;

import com.chua.common.support.annotations.SpiDefault;

import java.lang.reflect.Field;

/**
 * 格式�?
 *
 * @author CH
 */
@SpiDefault
public class NoneFormatter implements Formatter {
    @Override
    public Object format(Object value, Field field) {
        return value;
    }
}

