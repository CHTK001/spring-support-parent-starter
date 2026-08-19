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
 * Adapter
 *
 * @version 1.0.0
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
     * 创建 AnnotationPropertyValuesAdapter 实例
     * @param annotation annotation
     * @param propertyResolver propertyResolver
     * @param ignoreDefaultValue ignoreDefaultValue
     * @param ignoreAttributeNames ignoreAttributeNames
     * @param false false
     * @param false false
     * @param annotation annotation
     * @param propertyResolver propertyResolver
     * @param ignoreAttributeNames ignoreAttributeNames
     * @param propertyResolver propertyResolver
     * @param true true
     * @param ignoreAttributeNames ignoreAttributeNames
     * @param propertyName propertyName
     * @param old old
     * @param propertyName propertyName
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
     * 创建 AnnotationPropertyValuesAdapter 实例
     * @param annotation annotation
     * @param propertyResolver propertyResolver
     * @param ignoreAttributeNames ignoreAttributeNames
     * @param propertyResolver propertyResolver
     * @param true true
     * @param ignoreAttributeNames ignoreAttributeNames
     * @param propertyName propertyName
     * @param old old
     * @param propertyName propertyName
     */
    public AnnotationPropertyValuesAdapter(
            Annotation annotation, PropertyResolver propertyResolver, String... ignoreAttributeNames) {
        this(annotation, propertyResolver, true, ignoreAttributeNames);
    }

    @Override
    /** 获取PropertyValues */
    public PropertyValue[] getPropertyValues() {
        return delegate.getPropertyValues();
    }

    @Override
    /** 获取PropertyValue */
    public PropertyValue getPropertyValue(String propertyName) {
        return delegate.getPropertyValue(propertyName);
    }

    @Override
    /** ChangesSince */
    public PropertyValues changesSince(PropertyValues old) {
        return delegate.changesSince(old);
    }

    @Override
    /** Contains */
    public boolean contains(String propertyName) {
        return delegate.contains(propertyName);
    }

    @Override
    /** 是否Empty */
    public boolean isEmpty() {
        return delegate.isEmpty();
    }
}
