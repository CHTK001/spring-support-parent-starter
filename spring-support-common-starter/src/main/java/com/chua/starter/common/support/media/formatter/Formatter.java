package com.chua.starter.common.support.media.formatter;

// 注：Field 为 SPI 格式化接口签名要求，仅承载注解元数据，豁免
import java.lang.reflect.Field;

/**
 * 格式化
 *
 * @author CH
 * @since 4.0.0
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

