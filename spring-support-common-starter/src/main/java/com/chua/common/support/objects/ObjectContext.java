package com.chua.common.support.objects;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;

/**
 * 对象上下文接口，定义对象容器的基础操作契约
 *
 * @author CH
 */
public interface ObjectContext {
    /**
     * 根据名称和类型获取 Bean 实例
     *
     * @param <T>  目标类型泛型
     * @param name Bean 名称
     * @param type Bean 类型
     * @return Bean 实例，未找到时返回 null
     */
    <T> T getBean(String name, Class<T> type);

    /**
     * 根据类型获取 Bean 实例
     *
     * @param <T>  目标类型泛型
     * @param type Bean 类型
     * @return Bean 实例，未找到时返回 null
     */
    <T> T getBeanOfType(Class<T> type);

    /**
     * 安全地根据类型获取 Bean 实例，不会抛出异常
     *
     * @param <T>  目标类型泛型
     * @param type Bean 类型
     * @return Bean 实例，未找到时返回 null
     */
    <T> T getBeanOfTypeSafely(Class<T> type);

    /**
     * 根据类型获取所有匹配的 Bean 实例 Map（名称 -> 实例）
     *
     * @param <T>  目标类型泛型
     * @param type Bean 类型
     * @return 名称到 Bean 实例的映射
     */
    <T> Map<String, T> getBeanOfTypes(Class<T> type);

    /**
     * 根据类型获取所有匹配的 Bean 实例集合
     *
     * @param <T>  目标类型泛型
     * @param type Bean 类型
     * @return Bean 实例集合
     */
    <T> Collection<T> getBeanOfTypeCollection(Class<T> type);

    /**
     * 获取所有包含指定注解的 Bean 实例 Map（名称 -> 实例）
     *
     * @param annotationType 注解类型
     * @return 名称到 Bean 实例的映射
     */
    Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType);

    /**
     * 判断容器中是否包含指定名称的 Bean
     *
     * @param name Bean 名称
     * @return 包含返回 true，否则 false
     */
    boolean containsBean(String name);

    /**
     * 判断指定名称的 Bean 是否为单例
     *
     * @param name Bean 名称
     * @return 单例返回 true，否则 false
     */
    boolean isSingleton(String name);

    /**
     * 获取所有已注册的 Bean 定义名称集合
     *
     * @return Bean 名称集合
     */
    Collection<String> getBeanDefinitionNames();

    /**
     * 获取已注册的 Bean 定义总数
     *
     * @return Bean 数量
     */
    int getBeanDefinitionCount();

    /**
     * 判断容器中是否包含指定类型的 Bean
     *
     * @param <T>  目标类型泛型
     * @param type Bean 类型
     * @return 包含返回 true，否则 false
     */
    <T> boolean hasBeanOfType(Class<T> type);

    /**
     * 获取指定类型关联的所有 Bean 名称
     *
     * @param type Bean 类型
     * @return Bean 名称集合
     */
    Collection<String> getBeanNames(Class<?> type);

    /**
     * 获取指定类型的对象提供者，用于延迟获取或可选获取
     *
     * @param <T>          目标类型泛型
     * @param requiredType 所需的 Bean 类型
     * @return ObjectProvider 实例
     */
    <T> com.chua.common.support.objects.provider.ObjectProvider<T> getBeanProvider(Class<T> requiredType);

    /**
     * 获取当前上下文的环境配置接口
     *
     * @return Environment 实例
     */
    com.chua.common.support.objects.environment.Environment getEnvironment();

    /**
     * 发布事件，通知所有监听该事件的观察者
     *
     * @param event 事件对象
     * @return 事件处理后的结果
     */
    Object publish(Object event);
}
