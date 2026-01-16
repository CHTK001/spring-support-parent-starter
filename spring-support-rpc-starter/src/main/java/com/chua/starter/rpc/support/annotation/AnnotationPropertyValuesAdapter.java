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
 * @author CH
 * @version 1.0.0
 * @since 2.5.11
 */
public class AnnotationPropertyValuesAdapter implements PropertyValues {

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

    public AnnotationPropertyValuesAdapter(
            Annotation annotation,
            PropertyResolver propertyResolver,
            boolean ignoreDefaultValue,
            String... ignoreAttributeNames) {
        this.delegate = new MutablePropertyValues(
                AnnotationUtils.getAnnotationAttributes(annotation, false, false));
    }

    public AnnotationPropertyValuesAdapter(
            Annotation annotation, PropertyResolver propertyResolver, String... ignoreAttributeNames) {
        this(annotation, propertyResolver, true, ignoreAttributeNames);
    }

    @Override
    public PropertyValue[] getPropertyValues() {
        return delegate.getPropertyValues();
    }

    @Override
    public PropertyValue getPropertyValue(String propertyName) {
        return delegate.getPropertyValue(propertyName);
    }

    @Override
    public PropertyValues changesSince(PropertyValues old) {
        return delegate.changesSince(old);
    }

    @Override
    public boolean contains(String propertyName) {
        return delegate.contains(propertyName);
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }
}
