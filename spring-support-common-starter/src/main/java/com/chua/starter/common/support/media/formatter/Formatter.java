package com.chua.starter.common.support.media.formatter;

import java.lang.reflect.Field;

/**
 * 格式化
 *
 * @author CH
 */
public interface Formatter {
    /**
     * 格式化
     *
     * @param value 值
     * @param field
     * @return 結果
     */
    Object format(Object value, Field field);
}

