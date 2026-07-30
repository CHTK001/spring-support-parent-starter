package com.chua.starter.common.support.reflection;

import java.lang.reflect.Field;

/**
 * 字段访问工具。
 */
public final class FieldStation {

    private final Class<?> type;

    private FieldStation(Class<?> type) {
        this.type = type;
    }

    private FieldStation() {
        this.type = null;
    }

    public static FieldStation of(Class<?> type) {
        return new FieldStation(type);
    }

    public static FieldStation of(Object instance) {
        return new FieldStation(instance.getClass());
    }

    public Object getValue(String name) {
        try {
            Field field = findField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    public void setIgnoreNameValue(String name, Object value) {
        try {
            Field field = findField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (Exception ignored) {
        }
    }

    private Field findField(String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("No such field: " + name + " in " + type.getName());
        }
    }
}
