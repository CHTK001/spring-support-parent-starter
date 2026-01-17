package com.chua.starter.common.support.configuration;

import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;

/**
 * Spring Bean工具类
 * <p>
 * 提供获取Spring容器中Bean的静态方法。
 * </p>
 *
 * @author CH
 * @since 2024/12/07
 */
public class SpringBeanUtils {

    private static ApplicationContext applicationContext;
    private static Environment environment;
    private static RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * 设置ApplicationContext
     *
     * @param context ApplicationContext
     */
    public static void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
        if (context != null) {
            environment = context.getEnvironment();
        }
    }

    /**
     * 获取ApplicationContext
     *
     * @return ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext未初始化，请确保Spring容器已启动");
        }
        return applicationContext;
    }

    /**
     * 获取Environment
     *
     * @return Environment
     */
    public static Environment getEnvironment() {
        if (environment == null) {
            ApplicationContext context = getApplicationContext();
            environment = context.getEnvironment();
        }
        return environment;
    }

    /**
     * 设置RequestMappingHandlerMapping
     *
     * @param mapping RequestMappingHandlerMapping
     */
    public static void setRequestMappingHandlerMapping(RequestMappingHandlerMapping mapping) {
        requestMappingHandlerMapping = mapping;
    }

    /**
     * 获取RequestMappingHandlerMapping
     *
     * @return RequestMappingHandlerMapping
     */
    public static RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        if (requestMappingHandlerMapping == null) {
            ApplicationContext context = getApplicationContext();
            requestMappingHandlerMapping = context.getBean(RequestMappingHandlerMapping.class);
        }
        return requestMappingHandlerMapping;
    }

    /**
     * 获取Bean
     *
     * @param name Bean名称
     * @return Bean实例
     */
    public static Object getBean(String name) {
        return getApplicationContext().getBean(name);
    }

    /**
     * 获取Bean
     *
     * @param type Bean类型
     * @param <T>  Bean类型
     * @return Bean实例
     */
    public static <T> T getBean(Class<T> type) {
        return getApplicationContext().getBean(type);
    }

    /**
     * 解析占位符
     *
     * @param text 包含占位符的文本
     * @return 解析后的文本
     */
    public static String resolvePlaceholders(String text) {
        if (text == null) {
            return null;
        }
        return getEnvironment().resolvePlaceholders(text);
    }

    /**
     * 解析接口名称
     *
     * @param attributes 注解属性
     * @param beanClass Bean类
     * @return 接口名称
     */
    public static String resolveInterfaceName(Map<String, Object> attributes, Class<?> beanClass) {
        String interfaceName = (String) attributes.get("interfaceName");
        if (interfaceName != null && !interfaceName.isEmpty()) {
            return interfaceName;
        }
        Class<?> interfaceClass = (Class<?>) attributes.get("interfaceClass");
        if (interfaceClass != null) {
            return interfaceClass.getName();
        }
        // 如果都没有，尝试从 beanClass 获取接口
        Class<?>[] interfaces = beanClass.getInterfaces();
        if (interfaces.length > 0) {
            return interfaces[0].getName();
        }
        return beanClass.getName();
    }
}

