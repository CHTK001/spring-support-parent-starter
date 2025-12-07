package com.chua.starter.common.support.media.formatter;

import java.lang.reflect.Field;

/**
 * 格式�?
 *
 * @author CH
 */
public interface Formatter {
    /**
     * 格式�?
     *
     * @param value �?
     * @param field
     * @return 結果
     */
    Object format(Object value, Field field);
}

