package com.chua.starter.common.support.application;

import com.chua.common.support.function.Upgrade;
import com.chua.common.support.reflection.FieldStation;
import com.chua.common.support.utils.ClassUtils;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局工厂
 * @author CH
 * @since 2024/8/6
 */
public class GlobalSettingFactory {

    // 全局设置工厂类，用于管理和提供全局设置对象
    private static final GlobalSettingFactory INSTANCE = new GlobalSettingFactory();


    // 保存所有全局设置对象的集合，使用ConcurrentHashMap保证线程安全
    private static final Map<String, Object> GROUP = new ConcurrentHashMap<>();

    // 静态初始化块，用于初始化全局设置对象集合
    static {
        GROUP.put("sign", new Sign());
    }

    /**
     * 获取全局设置对象
     * @param type 设置名称
     * @param <T>   泛型标记
     * @return      对应的设置对象，如果不存在则返回null
     */
    public <T> T get(Class<T> type) {
        Collection<Object> values = GROUP.values();
        for (Object value : values) {
            if(type.isInstance(value)) {
                return (T) value;
            }
        }

        return null;
    }
    /**
     * 根据设置名称获取全局设置对象
     * @param group 设置名称
     * @param <T>   泛型标记
     * @return      对应的设置对象，如果不存在则返回null
     */
    public <T> T get(String group) {
        return (T) GROUP.get(group);
    }

    /**
     * 根据设置名称和类类型获取全局设置对象如果对象不存在则创建并返回
     * @param group 设置名称
     * @param clazz 对象的类类型
     * @param <T>   泛型标记
     * @return      对应的设置对象
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String group, Class<T> clazz) {
        T t = (T) GROUP.get(group);
        if(t == null) {
            GROUP.put(group, ClassUtils.forObject(clazz));
            return (T) GROUP.get(group);
        }

        return t;
    }

    /**
     * 注册全局设置对象
     * @param group 设置名称
     * @param t     要注册的设置对象
     * @param <T>   泛型标记
     */
    public <T> void register(String group, T t) {
        if(null == t && GROUP.containsKey(group)) {
            return;
        }
        GROUP.put(group, t);
    }

    /**
     * 获取全局设置工厂实例
     * @return 全局设置工厂实例
     */
    public static GlobalSettingFactory getInstance() {
        return INSTANCE;
    }

    /**
     * 设置全局设置对象的属性值
     * @param group 设置名称
     * @param name  属性名称
     * @param value 属性值
     * @param <T>   泛型标记
     */
    public <T>void set(String group, String name, Object value) {
        T t = get(group);
        if(null == t) {
            return ;
        }
        if(t instanceof Upgrade<?>) {
            ((Upgrade) t).upgrade(t);
            return;
        }
        FieldStation.of(t).setValue(name, value);
    }

    /**
     * 根据类类型设置全局设置对象的属性值
     * @param group 设置名称
     * @param type  对象的类类型
     * @param name  属性名称
     * @param value 属性值
     * @param <T>   泛型标记
     */
    @SuppressWarnings("ALL")
    public <T>void set(String group, Class<T> type, String name, Object value) {
        T t = get(group, type);
        if(null == t) {
            return ;
        }

        if(t instanceof Upgrade<?>) {
            ((Upgrade) t).upgrade(t);
            return;
        }
        FieldStation.of(t).setValue(name, value);
    }
}
