package com.chua.starter.swagger.support.resolver;

import com.chua.starter.swagger.support.annotation.HiddenParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
* 自定义模型解析器
* 用于过滤隐藏的模型类
*
* @since 2025/7/7 11:03
* @author CH
 */
public class CustomModelResolver extends ModelResolver {
    
    /**
    * 用户值注解类（可选依赖）
    */
    private static final Class<?> USER_VALUE_CLASS = loadUserValueClass();

    /**
    * 加载用户值注解类
    *
    * @return UserValue注解类，如果不存在则返回null
    */
    private static Class<?> loadUserValueClass() {
        try {
            return ClassUtils.forName("com.chua.starter.oauth.client.support.annotation.UserValue", 
                    CustomModelResolver.class.getClassLoader());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
    * 构造函数
    *
    * @param mapper 对象映射器
    */

    public CustomModelResolver(ObjectMapper mapper) {
        /**
        * 父。
        * @param mapper 映射器
        */
        super(mapper);
    }

    /**
    * 判断是否应该忽略类
    *
    * @param type 类型
    * @return 是否忽略
    */
    @Override
    protected boolean shouldIgnoreClass(Type type) {
        if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
 // 忽略hidden参数注解的类
            if (clazz.isAnnotationPresent(HiddenParam.class)) {
                return true;
            }
 // 忽略用户值注解的类（如果存在）
            if (USER_VALUE_CLASS != null && clazz.isAnnotationPresent(getUserValueAnnotationClass())) {
                return true;
            }
        }
        return super.shouldIgnoreClass(type);
    }

    /**
    * 获取 用户值 注解类型
    *
    * @return UserValue 注解 类
    */
    @SuppressWarnings("unchecked")
    private Class<? extends Annotation> getUserValueAnnotationClass() {
        return (Class<? extends Annotation>) USER_VALUE_CLASS;
    }
}
