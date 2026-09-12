package com.chua.common.support.base.converter.definition;

/**
* 枚举类型转换器，提供枚举类型的转换功能
*
* @author CH
* @since 4.0.0
 */
public class EnumTypeConverter implements com.chua.common.support.converter.definition.TypeConverter {
    /**
    * 获取该转换器支持的源类型
    *
    * @return 返回 {@link Enum} 类型，表示支持所有枚举类型的转换
     */
    @Override
    public Class<?> getType() {
        return Enum.class;
    }

    /**
    * 执行类型转换，当前实现直接返回源对象本身
    *
    * @param source 待转换的源对象
    * @return 转换后的对象，当前实现中直接返回 源 本身
     */
    @Override
    public Object convert(Object source) {
        return source;
    }
}
