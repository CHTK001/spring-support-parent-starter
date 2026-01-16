package com.chua.plugin.support.configuration;

/**
 * 向后兼容占位类。
 * <p>
 * 真实实现已迁移至 utils-support-spring-starter 模块中的
 * {@code com.chua.spring.support.plugin.GenericPluginRoutingFactoryBean}。
 * 该类仅保留以避免历史配置中直接引用本类导致编译错误。
 * </p>
 *
 * @param <T> 原始 Bean 类型
 */
public class GenericPluginRoutingFactoryBean<T>
        extends com.chua.spring.support.plugin.GenericPluginRoutingFactoryBean<T> {
}


