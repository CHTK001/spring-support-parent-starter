package com.chua.starter.datasource.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 类加载器
 *
 * @author CH
 * @since 2022-02-18
 */
@Slf4j
public class ClassUtils {

    protected static final Map<Class<?>, Type[]> ACTUAL = new ConcurrentHashMap<>();
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    /**
     * 实例化类
     *
     * @param s 类名
     * @return 类
     */
    public static Class<?> forName(String s) {
        try {
            return org.springframework.util.ClassUtils.forName(s, org.springframework.util.ClassUtils.getDefaultClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * 字符串转类
     *
     * @param s 字符串
     * @return 类
     */
    public static Class<?> forNames(String... s) {
        for (String s1 : s) {
            Class<?> aClass = ClassUtils.forName(s1);
            if (null == aClass) {
                continue;
            }
            return aClass;
        }
        return null;
    }


    /**
     * 获取名称
     *
     * @param parameterTypes 类型
     * @return 名称
     */
    public static String[] toTypeName(Class<?>[] parameterTypes) {
        if (null == parameterTypes || parameterTypes.length == 0) {
            return EMPTY_STRING_ARRAY;
        }
        String[] result = new String[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            result[i] = parameterType.getTypeName();
        }

        return result;
    }

    /**
     * 获取类的类加载器
     *
     * @param caller 类
     * @return ClassLoader
     */
    public static ClassLoader getCallerClassLoader(Class<?> caller) {
        Preconditions.checkNotNull(caller);
        ClassLoader classLoader = caller.getClassLoader();
        return null == classLoader ? ClassLoader.getSystemClassLoader() : classLoader;
    }

    /**
     * 获取泛型类型
     * <p>1.先判断父类泛型</p>
     * <p>2.判断接口泛型</p>
     *
     * @param value 类
     * @return 泛型类型
     */
    public static Type[] getActualTypeArguments(final Class<?> value, final Class<?>... includes) {
        return ACTUAL.computeIfAbsent(value, it -> {
            Type type = value.getGenericSuperclass();
            List<Type> types = new ArrayList<>();
            if (type instanceof ParameterizedType && container(type, includes)) {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                types.addAll(Arrays.asList(actualTypeArguments));
            }

            Type[] genericInterfaces = value.getGenericInterfaces();
            for (Type anInterface : genericInterfaces) {
                if (anInterface instanceof ParameterizedType && container(anInterface, includes)) {
                    ParameterizedType parameterizedType = (ParameterizedType) anInterface;
                    Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                    for (Type actualTypeArgument : actualTypeArguments) {
                        if (actualTypeArgument instanceof ParameterizedType) {
                            types.add(((ParameterizedType) actualTypeArgument).getRawType());
                        } else {
                            types.add(actualTypeArgument);
                        }
                    }
                }
            }
            return types.toArray(new Type[0]);
        });

    }

    /**
     * 是否包含类
     *
     * @param type     类
     * @param includes 类集合
     * @return 包含返回true
     */
    protected static boolean container(Type type, Class<?>[] includes) {
        if (null == type || null == includes || includes.length == 0) {
            return true;
        }

        String typeName = type.getTypeName();
        for (Class<?> include : includes) {
            int index = typeName.indexOf("<");
            if (index != -1) {
                typeName = typeName.substring(0, index);
            }
            if (typeName.equals(include.getName())) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param name        类
     * @param type        类型
     * @param classLoader 类加载器
     * @param <T>         类型
     * @return 对象
     */
    public static <T> T getObject(String name, Class<T> type, ClassLoader classLoader) {
        try {
            return getObject(org.springframework.util.ClassUtils.forName(name, classLoader), type);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * @param name 类
     * @param type 类型
     * @param <T>  类型
     * @return 对象
     */
    public static <T> T getObject(Class<?> name, Class<T> type) {
        if (null == name || !type.isAssignableFrom(name)) {
            return null;
        }
        ConditionalOnClass conditionalOnClass = name.getDeclaredAnnotation(ConditionalOnClass.class);
        if (null != conditionalOnClass) {
            String[] name1 = conditionalOnClass.name();
            boolean isSuccess = true;
            for (String s : name1) {
                Class<?> aClass = null;
                try {
                    aClass = org.springframework.util.ClassUtils.forName(s, org.springframework.util.ClassUtils.getDefaultClassLoader());
                } catch (Throwable ignored) {
                }
                if (null == aClass) {
                    isSuccess = false;
                    break;
                }
            }

            if (!isSuccess) {
                return null;
            }
        }

        try {
            return (T) name.newInstance();
        } catch (Throwable e) {
            log.error("", e);
        }
        return null;
    }

    /**
     * @param name 类
     * @param <T>  类型
     * @return 对象
     */
    public static <T> T getObject(String name) {
        if (Strings.isNullOrEmpty(name)) {
            return null;
        }
        return (T) getObject(forName(name));
    }

    /**
     * @param name 类
     * @param <T>  类型
     * @return 对象
     */
    public static <T> T getObject(Class<T> name) {
        return getObject(name, name);
    }

    /**
     * @param name 类
     * @param <T>  类型
     * @return 对象
     */
    public static <T> T forObject(Class<T> name) {
        return getObject(name, name);
    }

    /**
     * @param name          类
     * @param type          类型
     * @param defaultObject 默认值
     * @param <T>           类型
     * @return 对象
     */
    public static <T> T forObject(String name, Class<T> type, T defaultObject) {
        Object object = getObject(name);
        if (null == object || !type.isAssignableFrom(object.getClass())) {
            return defaultObject;
        }

        return (T) object;
    }

}
