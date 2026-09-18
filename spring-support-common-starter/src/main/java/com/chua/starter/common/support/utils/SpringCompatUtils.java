package com.chua.starter.common.support.utils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardMethodMetadata;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* tools
*
* @author CH
* @since 4.0.0
 */
public class SpringCompatUtils {

    /**
    * 私有构造函数，防止实例化
    */
    private SpringCompatUtils() {
    }

    /** 日志 */
    private static final Logger log = LoggerFactory.getLogger(SpringCompatUtils.class);
        private static volatile Boolean factoryMethodMetadataEnabled = null; // 工厂方法metadata已启用

    /**
    * 获取工厂方法申报类型
    *
    * @param annotatedBeanDefinition annotatedbeandefinition
    * @return 获取工厂方法申报类型的结果
    */
    public static String getFactoryMethodReturnType(AnnotatedBeanDefinition annotatedBeanDefinition) {
        try {
            if (isFactoryMethodMetadataEnabled()) {
                MethodMetadata factoryMethodMetadata = annotatedBeanDefinition.getFactoryMethodMetadata();
                return factoryMethodMetadata != null ? factoryMethodMetadata.getReturnTypeName() : null;
            } else {
                Object source = annotatedBeanDefinition.getSource();
                if (source instanceof StandardMethodMetadata methodMetadata) {
                    Method introspectedMethod = methodMetadata.getIntrospectedMethod();
                    if (introspectedMethod != null) {
                        return introspectedMethod.getReturnType().getName();
                    }
                }
            }
        } catch (Throwable e) {
            if (log.isInfoEnabled()) {
                log.info("get return type of AnnotatedBeanDefinition failed", e);
            }
        }
        return null;
    }

    /**
    * 获取工厂方法metadata
    *
    * @param annotatedBeanDefinition annotatedbeandefinition
    * @return 获取工厂方法metadata的结果
    */
    public static MethodMetadata getFactoryMethodMetadata(AnnotatedBeanDefinition annotatedBeanDefinition) {
        if (isFactoryMethodMetadataEnabled()) {
            return annotatedBeanDefinition.getFactoryMethodMetadata();
        } else {
            Object source = annotatedBeanDefinition.getSource();
            if (source instanceof StandardMethodMetadata) {
                return (MethodMetadata) source;
            }
            return null;
        }
    }


    /**
    * 获取 the generic 类型 的 申报 类型 的 the 方法.
    *
    * <pre>
    *  Source method:
    *  ReferenceBean&lt;DemoService> demoService()
    *
    *  Result: DemoService.class
    * </pre>
    *
    * @param factoryMethodMetadata
    * @return
    */
    public static Class getGenericTypeOfReturnType(MethodMetadata factoryMethodMetadata) {
        if (factoryMethodMetadata instanceof StandardMethodMetadata) {
            Method introspectedMethod = ((StandardMethodMetadata) factoryMethodMetadata).getIntrospectedMethod();
            Type returnType = introspectedMethod.getGenericReturnType();
            if (returnType instanceof ParameterizedType parameterizedType) {
                Type actualTypeArgument = parameterizedType.getActualTypeArguments()[0];
                if (actualTypeArgument instanceof Class) {
                    return (Class) actualTypeArgument;
                }
            }
        }
        return null;
    }

    /**
    * 是否工厂方法metadata已启用
    *
    * @return 是否工厂方法metadata已启用的结果
    */
    public static boolean isFactoryMethodMetadataEnabled() {
        if (factoryMethodMetadataEnabled == null) {
            try {
 // 检查 annotatedBeandefinition.获取工厂方法metadata() 自 spring 4.1
                AnnotatedBeanDefinition.class.getMethod("getFactoryMethodMetadata");

 // 检查 方法metadata.获取申报类型名称() 自 spring 4.2
                MethodMetadata.class.getMethod("getReturnTypeName");

                factoryMethodMetadataEnabled = true;
            } catch (NoSuchMethodException e) {
                factoryMethodMetadataEnabled = false;
            }
        }
        return factoryMethodMetadataEnabled;
    }


}

