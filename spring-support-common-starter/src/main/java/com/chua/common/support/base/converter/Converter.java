package com.chua.common.support.base.converter;

/**
 * 类型转换器接口
 * <p>
 * 定义通用的类型转换契约，将源类型 T 转换为目标类型 R。
 * </p>
 *
 * @param <T> 源类型
 * @param <R> 目标类型
 * @author CH
 * @since 4.0.0
 */
public interface Converter<T, R> {

    /**
     * 执行类型转换
     *
     * @param source 源对象
     * @return 转换后的目标对象
     */
    R convert(T source);
}
