package com.chua.common.support.converter;

/**
 * 类型转换器工具类，提供基本类型之间的转换功能
 *
 * @author CH
 */
public class Converter {

    /**
     * 将源对象按需转换为指定的目标类型
     * <p>支持 String、Long/long、Integer/int、Double/double、Boolean/boolean 等常见类型的自动转换。
     * 如果源对象已经是目标类型则直接返回；否则依次尝试数值解析或字符串解析。</p>
     *
     * @param <T>        目标类型泛型
     * @param source     待转换的源对象，允许为 null
     * @param targetType 目标类型的 Class 对象
     * @return 转换后的目标类型对象，若转换失败或 source 为 null 则返回 null
     */
    @SuppressWarnings("unchecked")
    public static <T> T convertIfNecessary(Object source, Class<T> targetType) {
        if (source == null) return null;
        if (targetType.isInstance(source)) return (T) source;
        if (targetType == String.class) return (T) source.toString();
        if (targetType == Long.class || targetType == long.class) {
            if (source instanceof Number) return (T) Long.valueOf(((Number) source).longValue());
            try { return (T) Long.valueOf(source.toString()); } catch (Exception ignored) {}
        }
        if (targetType == Integer.class || targetType == int.class) {
            if (source instanceof Number) return (T) Integer.valueOf(((Number) source).intValue());
            try { return (T) Integer.valueOf(source.toString()); } catch (Exception ignored) {}
        }
        if (targetType == Double.class || targetType == double.class) {
            if (source instanceof Number) return (T) Double.valueOf(((Number) source).doubleValue());
            try { return (T) Double.valueOf(source.toString()); } catch (Exception ignored) {}
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (source instanceof Boolean) return (T) source;
            try { return (T) Boolean.valueOf(source.toString()); } catch (Exception ignored) {}
        }
        return null;
    }
}
