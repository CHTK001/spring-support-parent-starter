package com.chua.starter.lock.support;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 反射注解属性辅助工具。
 *
 * @author CH
 * @since 2026-03-28
 */
public final class AnnotationAttributeSupport {

    private AnnotationAttributeSupport() {
    }

    public static boolean isPresent(String annotationClassName) {
        return ClassUtils.isPresent(annotationClassName, AnnotationAttributeSupport.class.getClassLoader());
    }

    @SuppressWarnings("unchecked")
    public static Annotation findAnnotation(Method method, String annotationClassName) {
        if (!isPresent(annotationClassName)) {
            return null;
        }

        Class<? extends Annotation> annotationType =
                (Class<? extends Annotation>) ClassUtils.resolveClassName(annotationClassName, AnnotationAttributeSupport.class.getClassLoader());
        return AnnotationUtils.findAnnotation(method, annotationType);
    }

    public static Map<String, Object> attributes(Annotation annotation) {
        return AnnotationUtils.getAnnotationAttributes(annotation);
    }

    public static String getString(Map<String, Object> attributes, String key, String defaultValue) {
        Object value = attributes.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    public static long getLong(Map<String, Object> attributes, String key, long defaultValue) {
        Object value = attributes.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return defaultValue;
    }

    public static boolean getBoolean(Map<String, Object> attributes, String key, boolean defaultValue) {
        Object value = attributes.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return defaultValue;
    }

    public static String getEnumName(Map<String, Object> attributes, String key, String defaultValue) {
        Object value = attributes.get(key);
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return defaultValue;
    }
}
