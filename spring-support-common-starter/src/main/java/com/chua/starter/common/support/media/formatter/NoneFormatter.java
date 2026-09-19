package com.chua.starter.common.support.media.formatter;


import com.chua.common.support.spi.annotations.SpiDefault;

// 注：Field 为 SPI 格式化接口签名要求，仅承载注解元数据，豁免
import java.lang.reflect.Field;

/**
 * 空格式化器（默认实现）
 * <p>
 * 不做任何格式化处理，直接返回原始值。
 * 作为 {@link Formatter} 的默认实现。
 * </p>
 *
 * @版本 1.0.0
 * @see Formatter
 * @author CH
 * @since 4.0.0
 */
@SpiDefault
public class NoneFormatter implements Formatter {
    @Override
    /** 格式化 */
    public Object format(Object value, Field field) {
        return value;
    }
}
