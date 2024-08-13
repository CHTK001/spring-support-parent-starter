package com.chua.starter.common.support.application;

import com.chua.common.support.utils.ClassUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局工厂
 * @author CH
 * @since 2024/8/6
 */
public class GlobalSettingFactory {


    private static final GlobalSettingFactory INSTANCE = new GlobalSettingFactory();


    private static final Map<Class<?>, Object> CACHE = new ConcurrentHashMap<>();

    static {
        CACHE.put(Sign.class, new Sign());
    }
    /**
     * 获取实例
     * @param clazz 类
     * @param <T> 类型
     * @return 实例
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz) {
        T t = (T) CACHE.get(clazz);
        if(t == null) {
            CACHE.put(clazz, ClassUtils.forObject(clazz));
            return (T) CACHE.get(clazz);
        }

        return t;
    }

    /**
     * 设置实例
     * @param clazz 类
     * @param t 实例
     * @param <T> 类型
     */
    public <T> void register(Class<T> clazz, T t) {
        if(null == t) {
            return;
        }
        CACHE.put(clazz, t);
    }

    public <T> void register(T t) {
        if(null == t) {
            return;
        }
        CACHE.put(t.getClass(), t);
    }

    /**
     * 获取实例
     * @return 实例
     */
    public static GlobalSettingFactory getInstance() {
        return INSTANCE;
    }
}
