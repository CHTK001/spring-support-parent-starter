package com.chua.starter.common.support.application;

import com.chua.common.support.collection.ConcurrentReferenceHashMap;
import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.function.Upgrade;
import com.chua.common.support.reflection.FieldStation;
import com.chua.common.support.utils.ClassUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局工厂
 *
 * @author CH
 * @since 2024/8/6
 */
public class GlobalSettingFactory {

    // 全局设置工厂类，用于管理和提供全局设置对象
    private static final GlobalSettingFactory INSTANCE = new GlobalSettingFactory();


    // 保存所有全局设置对象的集合，使用ConcurrentHashMap保证线程安全
    private static final Map<String, List<Object>> GROUP = new ConcurrentHashMap<>();
    private static final Map<String, String> CHANGE = new ConcurrentReferenceHashMap<>(512);

    // 静态初始化块，用于初始化全局设置对象集合
    static {
        GROUP.computeIfAbsent("sign", it -> new LinkedList<>()).add(new Sign());
    }

    /**
     * 获取全局设置对象
     *
     * @param type 设置名称
     * @param <T>  泛型标记
     * @return 对应的设置对象，如果不存在则返回null
     */
    public <T> T get(Class<T> type) {
        Collection<List<Object>> values = GROUP.values();
        for (List<Object> value : values) {
            for (Object item : value) {
                if (type.isInstance(item)) {
                    return (T) item;
                }
            }
        }

        return null;
    }

    /**
     * 根据设置名称获取全局设置对象
     *
     * @param group 设置名称
     * @param <T>   泛型标记
     * @return 对应的设置对象，如果不存在则返回null
     */
    public <T> List<T> get(String group) {
        return (List<T>) GROUP.get(group);
    }

    /**
     * 根据设置名称和类类型获取全局设置对象如果对象不存在则创建并返回
     *
     * @param group 设置名称
     * @param clazz 对象的类类型
     * @param <T>   泛型标记
     * @return 对应的设置对象
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String group, Class<T> clazz) {
        List<T> t = (List<T>) GROUP.get(group);
        if (t == null) {
            T t1 = ClassUtils.forObject(clazz);
            register(group, t1);
            return t1;
        }

        for (T t1 : t) {
            if(clazz.isInstance(t1)) {
                return t1;
            }
        }
        return null;
    }

    /**
     * 注册全局设置对象
     *
     * @param group 设置名称
     * @param t     要注册的设置对象
     * @param <T>   泛型标记
     */
    public <T> void register(String group, T t) {
        if (null == t) {
            return;
        }
        List<Object> objects = GROUP.get(group);
        if(null == objects) {
            GROUP.put(group, new LinkedList<>());
            objects = GROUP.get(group);
        }

        for (Object object : objects) {
            if(object.getClass().isAssignableFrom(t.getClass())) {
                return;
            }
        }

        objects.add(t);
    }

    /**
     * 获取全局设置工厂实例
     *
     * @return 全局设置工厂实例
     */
    public static GlobalSettingFactory getInstance() {
        return INSTANCE;
    }

    /**
     * 为指定组(Group)和名称(Name)的配置项设置新值，如果配置项自上次检查以来未发生改变
     * 此方法设计用于确保只有在相关配置项未被外部更改的情况下，才更新其值 该设计有助于避免并发修改带来的问题
     *
     * @param group 配置项所属的组，用于定位特定的配置项 必须是有效的组名称
     * @param name  配置项的名称，用于精确识别特定的配置项 必须是有效的配置项名称
     * @param value 要设置的新值，可以是任何类型的对象 如果配置项自上次检查后未改变，将设置此值
     * @param <T>   值的类型，泛型使用以支持各种类型的配置项值
     */
    public synchronized  <T> void setIfNoChange(String group, String name, Object value) {
        if (CHANGE.containsKey(group + name)) {
            return;
        }

        set(group, name, value);
        CHANGE.put(group + name, CommonConstant.SYMBOL_EMPTY);
    }

    /**
     * 设置全局设置对象的属性值
     *
     * @param group 设置名称
     * @param name  属性名称
     * @param value 属性值
     * @param <T>   泛型标记
     */
    public synchronized <T> void set(String group, String name, Object value) {
        List<T> ts = get(group);
        if (null == ts) {
            return;
        }
        for (T t : ts) {
            FieldStation.of(t).setValue(name, value);
            if (t instanceof Upgrade<?>) {
                ((Upgrade) t).upgrade(t);
            }
        }
    }

    /**
     * 根据类类型设置全局设置对象的属性值
     *
     * @param group 设置名称
     * @param type  对象的类类型
     * @param name  属性名称
     * @param value 属性值
     * @param <T>   泛型标记
     */
    @SuppressWarnings("ALL")
    public synchronized <T> void set(String group, Class<T> type, String name, Object value) {
        T t = get(group, type);
        if (null == t) {
            return;
        }

        FieldStation.of(t).setValue(name, value);
        if (t instanceof Upgrade<?>) {
            ((Upgrade) t).upgrade(t);
            return;
        }
    }
}
