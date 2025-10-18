package com.chua.starter.common.support.application;

import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.function.Upgrade;
import com.chua.common.support.reflection.FieldStation;
import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;

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
    public static String PREFIX = "";


    static final Map<String, Object> CONFIG = new ConcurrentHashMap<>();
    static final Map<String, List<Object>> GROUP = new ConcurrentHashMap<>();

    /**
     * 获取全局设置对象
     *
     * @param type 设置名称
     * @param <T>  泛型标记
     * @return 对应的设置对象，如果不存在则返回null
     */
    public <T> T get(Class<T> type) {
        for (Map.Entry<String, List<Object>> entry : GROUP.entrySet()) {
            List<Object> value = entry.getValue();
            for (Object o : value) {
                if (type.isAssignableFrom(o.getClass())) {
                    return (T) o;
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
        return (List<T>) GROUP.get(PREFIX + group);
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
        List<T> t = (List<T>) GROUP.get(PREFIX + group);
        if (t == null) {
            T t1 = ClassUtils.forObject(clazz);
            register(group, t1);
            return t1;
        }

        for (T t1 : t) {
            if(null == t1) {
                continue;
            }
            if (clazz.isInstance(t1)) {
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
        List<Object> objects = GROUP.get(PREFIX + group);
        if (null == objects) {
            GROUP.put(PREFIX + group, new LinkedList<>());
            objects = GROUP.get(PREFIX + group);
        }

        for (Object object : objects) {
            if (object.getClass().isAssignableFrom(t.getClass())) {
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
    public synchronized <T> void setIfNoChange(String group, String name, Object value) {
        if (CONFIG.containsKey(PREFIX + group + name)) {
            return;
        }

        set(group, name, value);
        CONFIG.put(PREFIX + group + name, CommonConstant.SYMBOL_EMPTY);
    }

    /**
     * 设置全局设置对象的属性值
     *
     * @param group  设置名称
     * @param params 属性
     * @param <T>    泛型标记
     */
    public synchronized <T> void set(String group, Map<String, Object> params) {
        if (MapUtils.isEmpty(params)) {
            return;
        }

        List<T> ts = get(group);
        if (null == ts) {
            return;
        }
        for (T t : ts) {
            params.forEach((name, value) -> FieldStation.of(t).setIgnoreNameValue(name, value));
            if (t instanceof Upgrade<?>) {
                ((Upgrade) t).upgrade(t);
                SpringBeanUtils.getApplicationContext().publishEvent(t);
            }
        }
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
            FieldStation.of(t).setIgnoreNameValue(name, value);
            if (t instanceof Upgrade<?>) {
                ((Upgrade) t).upgrade(t);
                SpringBeanUtils.getApplicationContext().publishEvent(t);
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

        FieldStation.of(t).setIgnoreNameValue(name, value);
        if (t instanceof Upgrade<?>) {
            ((Upgrade) t).upgrade(t);
            return;
        }
    }
}
