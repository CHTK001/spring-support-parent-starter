package com.chua.starter.common.support.utils;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.io.Resource;

import java.io.File;
import java.util.Map;

/**
 * bean定义工具
 *
 * @author CH
 */
public class BeanDefinitionUtils {

    /**
     * 转换
     *
     * @param resource 资源
     * @return {@link Class}<{@link ?}>
     * @throws Exception 异常
     */
    public static Class<?> getType(Resource resource) throws Exception {
        // 获取类加载器
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        // 从Resource对象中获取文件路�?
        String filePath = resource.getFile().getAbsolutePath();

        // 根据文件路径创建Class对象
        return classLoader.loadClass(getClassNameFromFilePath(filePath.replace(File.separator, "/")));
    }

    /**
     * 从文件路径获取类�?
     *
     * @param filePath 文件路径
     * @return {@link String}
     */
    private static String getClassNameFromFilePath(String filePath) {
        int lastIndexOfSlash = filePath.lastIndexOf("/");
        if (lastIndexOfSlash != -1 && lastIndexOfSlash < filePath.length() - 6) {
            return filePath.substring(lastIndexOfSlash + 1).replace(".class", "").trim();
        } else {
            throw new IllegalArgumentException("Invalid file path.");
        }
    }
    /**
     * 注册bean
     *
     * @param registry 注册�?
     * @param aClass   定义
     * @param beanMap  参数
     */
    public static void registerTypePropertiesBeanDefinition(BeanDefinitionRegistry registry, Class<?> aClass, Map beanMap) {
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(aClass);
        beanMap.forEach((k, v) -> {
            beanDefinitionBuilder.addPropertyValue(k.toString(), v);
        });
        registry.registerBeanDefinition(aClass.getName(), beanDefinitionBuilder.getBeanDefinition());
    }

    /**
     * 注册bean
     *
     * @param pre      bean name pre
     * @param registry 注册�?
     * @param aClass   定义
     * @param beanMap  参数
     */
    public static void registerTypePropertiesBeanDefinition(String pre, BeanDefinitionRegistry registry, Class<?> aClass, Map beanMap) {
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(aClass);
        beanMap.forEach((k, v) -> {
            beanDefinitionBuilder.addPropertyValue(k.toString(), v);
        });
        registry.registerBeanDefinition(pre + "@" + aClass.getName(), beanDefinitionBuilder.getBeanDefinition());
    }
}

