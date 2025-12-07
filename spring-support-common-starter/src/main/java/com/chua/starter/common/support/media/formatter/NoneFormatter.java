package com.chua.starter.common.support.media.formatter;

import com.chua.common.support.annotations.SpiDefault;

import java.lang.reflect.Field;

/**
 * 空格式化器（默认实现）
 * <p>
 * 不做任何格式化处理，直接返回原始值。
 * 作为 {@link Formatter} 的默认实现。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @see Formatter
 */
@SpiDefault
public class NoneFormatter implements Formatter {
    @Override
    public Object format(Object value, Field field) {
        return value;
    }
}

