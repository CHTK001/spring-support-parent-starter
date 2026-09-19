package com.chua.starter.rpc.support.annotation;


import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.PropertyResolver;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * {@link Annotation}
 * <p>
 * {@link PropertyValues}
 * 适配器
 *
 * @版本 1.0.0
 * @since 2.5.11
 * @author CH
 */
public class AnnotationPropertyValuesAdapter implements PropertyValues {

    /** Delegate */
    private final PropertyValues delegate;

    /**
    * @param attributes
    * @param propertyResolver
    * @param ignoreAttributeNames
    * @since 2.7.3
    */
    public AnnotationPropertyValuesAdapter(
            Map<String, Object> attributes, PropertyResolver propertyResolver, String... ignoreAttributeNames) {
        this.delegate = new MutablePropertyValues(attributes);
    }

    /**
     * 创建 注解财产值适配器 实例
     * @param annotation 注解
     * @param propertyResolver 财产解析器
     * @param ignoreDefaultValue ignore默认值
     * @param ignoreAttributeNames ignoreattribute名称
     * @param false false
     * @param false false
     * @param annotation 注解
     * @param propertyResolver 财产解析器
     * @param ignoreAttributeNames ignoreattribute名称
     * @param propertyResolver 财产解析器
     * @param true true
     * @param ignoreAttributeNames ignoreattribute名称
     * @param propertyName 财产名称
     * @param old 旧
     * @param propertyName 财产名称
     */
    public AnnotationPropertyValuesAdapter(
            Annotation annotation,
            PropertyResolver propertyResolver,
            boolean ignoreDefaultValue,
            String... ignoreAttributeNames) {
        this.delegate = new MutablePropertyValues(
                AnnotationUtils.getAnnotationAttributes(annotation, false, false));
    }

    /**
     * 创建 注解财产值适配器 实例
     * @param annotation 注解
     * @param propertyResolver 财产解析器
     * @param ignoreAttributeNames ignoreattribute名称
     * @param propertyResolver 财产解析器
     * @param true true
     * @param ignoreAttributeNames ignoreattribute名称
     * @param propertyName 财产名称
     * @param old 旧
     * @param propertyName 财产名称
     */
    public AnnotationPropertyValuesAdapter(
            Annotation annotation, PropertyResolver propertyResolver, String... ignoreAttributeNames) {
        /**
         * this。
         * @param annotation 注解
         * @param propertyResolver 财产解析器
         * @param true true
         * @param ignoreAttributeNames ignoreattribute名称
         */
        this(annotation, propertyResolver, true, ignoreAttributeNames);
    }

    @Override
    /** 获取财产值 */
    public PropertyValue[] getPropertyValues() {
        return delegate.getPropertyValues();
    }

    @Override
    /** 获取财产值 */
    public PropertyValue getPropertyValue(String propertyName) {
        return delegate.getPropertyValue(propertyName);
    }

    @Override
    /** 改变自 */
    public PropertyValues changesSince(PropertyValues old) {
        return delegate.changesSince(old);
    }

    @Override
    /** Contains */
    public boolean contains(String propertyName) {
        return delegate.contains(propertyName);
    }

    @Override
    /** 是否空 */
    public boolean isEmpty() {
        return delegate.isEmpty();
    }
}
