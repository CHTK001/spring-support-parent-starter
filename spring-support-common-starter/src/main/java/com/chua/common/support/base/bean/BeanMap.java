package com.chua.common.support.base.bean;

import com.chua.common.support.reflection.ReflectUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Bean 属性映射工具类
 * <p>
 * 提供对象属性与 Map 之间的双向转换能力。
 * </p>
 *
 * @author CH
 */
public class BeanMap extends HashMap<String, Object> {

    /**
     * 根据指定的源对象创建并返回一个 BeanMap 实例
     * <p>该方法通过反射获取源对象的所有可读属性（排除 class 属性），将其属性名-属性值对填充到返回的 BeanMap 中。</p>
     *
     * @param source 源对象，不可为 null
     * @return 包含源对象所有非空属性的 BeanMap 实例
     */
    public static BeanMap of(Object source) {
        return new BeanMap(source);
    }

    /**
     * 空参构造函数，创建一个空的 BeanMap 实例
     */
    public BeanMap() {
        super();
    }

    /**
     * 根据源对象构造 BeanMap，通过 Java Beans 内省机制获取所有可读属性
     * <p>利用 {@link java.beans.Introspector} 获取源对象的 BeanInfo，
     * 遍历所有属性描述器，通过 getter 方法获取属性值（非 null 值）放入 Map。</p>
     *
     * @param source 源对象，允许为 null（此时 Map 保持为空）
     */
    public BeanMap(Object source) {
        super();
        if (null != source) {
            java.beans.BeanInfo beanInfo = null;
            try {
                beanInfo = java.beans.Introspector.getBeanInfo(source.getClass());
            } catch (java.beans.IntrospectionException e) {
                throw new RuntimeException(e);
            }
            java.beans.PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
            if (null != propertyDescriptors) {
                for (java.beans.PropertyDescriptor property : propertyDescriptors) {
                    String key = property.getName();
                    if ("class".equals(key)) {
                        continue;
                    }
                    try {
                        java.lang.reflect.Method getter = property.getReadMethod();
                        if (null != getter) {
                            Object value = ReflectUtils.invoke(source, getter.getName(), getter.getReturnType());
                            if (value != null) {
                                put(key, value);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
