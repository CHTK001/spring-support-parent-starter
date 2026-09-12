package com.chua.spring.support.configuration;

import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Bean 工具类
 * <p>
 * 提供获取 Spring Bean 的静态方法
 * </p>
 *
 * @author CH
 * @since 4.0.0
 */
public class SpringBeanUtils {

    /**
     * springBean工具。
     */
    private SpringBeanUtils() {
    }

    /** Application上下文 */
    private static ApplicationContext applicationContext;

    /**
      * 设置 application上下文
     *
     * @param ctx application上下文 实例
     */
    public static void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx;
    }

    /**
     * 根据名称获取 Bean
     *
     * @param name Bean 名称
     * @param <T>  Bean 类型
     * @return Bean 实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) {
        if (applicationContext == null) {
            return null;
        }
        return (T) applicationContext.getBean(name);
    }

    /**
     * 根据类型获取 Bean
     *
     * @param clz Bean 类型
     * @param <T> Bean 类型
     * @return Bean 实例
     */
    public static <T> T getBean(Class<T> clz) {
        if (applicationContext == null) {
            return null;
        }
        return applicationContext.getBean(clz);
    }

    /**
      * 获取 application上下文（允许返回 空）
     *
     * @return ApplicationContext 实例
     */
    public static ApplicationContext getApplicationContextOrNull() {
        return applicationContext;
    }

    /**
      * 获取 application上下文
     *
     * @return ApplicationContext 实例
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
      * 获取 环境
     *
     * @return Environment 实例
     */
    public static Environment getEnvironment() {
        return getBean(Environment.class);
    }

    /**
      * 获取 请求mapping处理器mapping
     *
     * @return HandlerMapping 实例
     */
    public static RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        HandlerMapping handlerMapping = getBean("requestMappingHandlerMapping", HandlerMapping.class);
        if (handlerMapping instanceof RequestMappingHandlerMapping rmhm) {
            return rmhm;
        }
        return null;
    }

    /**
     * 获取指定类型的所有 Bean 实例。
     *
     * @param clz Bean 类型
     * @param <T> Bean 类型参数
     * @return 所有该类型的 Bean 集合
     */
    public static <T> Collection<T> getBeanList(Class<T> clz) {
        if (applicationContext == null) {
            return Collections.emptyList();
        }
        return applicationContext.getBeansOfType(clz).values();
    }

    /**
     * 根据名称和类型获取 Bean
     *
     * @param name Bean 名称
     * @param clz  Bean 类型
     * @param <T>  Bean 类型
     * @return Bean 实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name, Class<T> clz) {
        if (applicationContext == null) {
            return null;
        }
        return (T) applicationContext.getBean(name);
    }

    /**
     * 将 Spring Bean 依赖注入到指定对象中。
     *
     * @param bean 待注入的对象
     */
    public static void autowireBean(Object bean) {
        if (applicationContext != null) {
            applicationContext.getAutowireCapableBeanFactory().autowireBean(bean);
        }
    }
}
