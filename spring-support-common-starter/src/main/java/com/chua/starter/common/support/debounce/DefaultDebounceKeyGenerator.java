package com.chua.starter.common.support.debounce;

import com.chua.common.support.utils.StringUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * 防抖生成器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/03/10
 */
public class DefaultDebounceKeyGenerator implements DebounceKeyGenerator{


    @Override
    public String getKey(String prefix, Method method, Object[] args) {
        //获取Method对象上所有的注解
        final Parameter[] parameters = method.getParameters();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parameters.length; i++) {
            final DebounceParam keyParam = parameters[i].getAnnotation(DebounceParam.class);
            //如果属性不是RequestKeyParam注解，则不处理
            if (keyParam == null) {
                continue;
            }
            //如果属性是RequestKeyParam注解，则拼接 连接符 "& + RequestKeyParam"
            sb.append('&').append(args[i]);
        }
        //如果方法上没有加RequestKeyParam注解
        if (StringUtils.isEmpty(sb.toString())) {
            //获取方法上的多个注解（为什么是两层数组：因为第二层数组是只有一个元素的数组）
            final Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            //循环注解
            for (int i = 0; i < parameterAnnotations.length; i++) {
                final Object object = args[i];
                //获取注解类中所有的属性字段
                final Field[] fields = object.getClass().getDeclaredFields();
                for (Field field : fields) {
                    //判断字段上是否有RequestKeyParam注解
                    final DebounceParam annotation = field.getAnnotation(DebounceParam.class);
                    //如果没有，跳过
                    if (annotation == null) {
                        continue;
                    }
                    //如果有，设置Accessible为true（为true时可以使用反射访问私有变量，否则不能访问私有变量）
                    field.setAccessible(true);
                    //如果属性是RequestKeyParam注解，则拼接 连接符" & + RequestKeyParam"
                    sb.append('&').append(ReflectionUtils.getField(field, object));
                }
            }
        }
        //返回指定前缀的key
        return prefix + sb;
    }
}
