package com.chua.common.support.converter.definition;

/**
 * 类型转换器接口，定义类型转换的基础契约
 *
 * @author CH
 */
public interface TypeConverter {
    /**
     * 获取该转换器支持的源类型
     *
     * @return 支持的源类型 Class
     */
    Class<?> getType();

    /**
     * 将源对象转换为目标类型的对象
     *
     * @param source 待转换的源对象
     * @return 转换后的结果对象
     */
    Object convert(Object source);
}
