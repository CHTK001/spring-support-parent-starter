package com.chua.starter.common.support.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardMethodMetadata;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * tools
 *
 * @author CH
 */
@Slf4j
public class SpringCompatUtils {

    private static volatile Boolean factoryMethodMetadataEnabled = null;

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
     * Get the generic type of return type of the method.
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

    public static boolean isFactoryMethodMetadataEnabled() {
        if (factoryMethodMetadataEnabled == null) {
            try {
                //check AnnotatedBeanDefinition.getFactoryMethodMetadata() since spring 4.1
                AnnotatedBeanDefinition.class.getMethod("getFactoryMethodMetadata");

                // check MethodMetadata.getReturnTypeName() since spring 4.2
                MethodMetadata.class.getMethod("getReturnTypeName");

                factoryMethodMetadataEnabled = true;
            } catch (NoSuchMethodException e) {
                factoryMethodMetadataEnabled = false;
            }
        }
        return factoryMethodMetadataEnabled;
    }


}

