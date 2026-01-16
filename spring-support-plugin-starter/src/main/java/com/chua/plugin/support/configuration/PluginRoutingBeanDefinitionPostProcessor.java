package com.chua.plugin.support.configuration;

import com.chua.common.support.core.annotation.PluginRouting;
import com.chua.plugin.support.properties.PluginProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * 插件路由 BeanDefinition 统一包装处理器。
 * <p>
 * 作用：在 BeanDefinition 级别统一将标记了 {@link PluginRouting} 的 Bean
 * 包装为 {@link GenericPluginRoutingFactoryBean}，由后者接入插件路由能力。
 * </p>
 *
 * @author CH
 * @since 2026/01/09
 */
@Slf4j
@RequiredArgsConstructor
public class PluginRoutingBeanDefinitionPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    /**
     * 插件配置属性。
     */
    private final PluginProperties pluginProperties;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        String[] beanNames = registry.getBeanDefinitionNames();
        int wrappedCount = 0;
        for (String beanName : beanNames) {
            BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
            if (!shouldWrap(beanName, beanDefinition)) {
                continue;
            }
            Class<?> beanClass = resolveBeanClass(beanDefinition);
            if (beanClass == null || !beanClass.isAnnotationPresent(PluginRouting.class)) {
                continue;
            }
            wrapWithRoutingFactoryBean(beanName, beanDefinition, beanClass);
            wrappedCount++;
        }
        if (wrappedCount > 0) {
            log.info("[插件系统][路由]Plugin routing enabled, wrapped {} beanDefinition(s) with GenericPluginRoutingFactoryBean", wrappedCount);
        } else {
            log.debug("[插件系统][路由]Plugin routing post processor completed, no beanDefinitions wrapped");
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    @Override
    public int getOrder() {
        return PriorityOrdered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean shouldWrap(String beanName, BeanDefinition beanDefinition) {
        String beanClassName = beanDefinition.getBeanClassName();
        if (!StringUtils.hasText(beanClassName)) {
            return false;
        }
        if (GenericPluginRoutingFactoryBean.class.getName().equals(beanClassName)) {
            return false;
        }
        String[] includes = pluginProperties.getRouting().getIncludePatterns();
        if (includes != null && includes.length > 0) {
            boolean matchInclude = matchPatterns(beanName, beanClassName, includes);
            if (!matchInclude) {
                return false;
            }
        }
        String[] excludes = pluginProperties.getRouting().getExcludePatterns();
        if (excludes != null && excludes.length > 0) {
            boolean matchExclude = matchPatterns(beanName, beanClassName, excludes);
            if (matchExclude) {
                return false;
            }
        }
        return true;
    }

    private boolean matchPatterns(String beanName, String beanClassName, String[] patterns) {
        for (String pattern : patterns) {
            if (!StringUtils.hasText(pattern)) {
                continue;
            }
            String trimmed = pattern.trim();
            if (beanName.startsWith(trimmed) || beanClassName.startsWith(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private Class<?> resolveBeanClass(BeanDefinition beanDefinition) {
        String className = beanDefinition.getBeanClassName();
        if (!StringUtils.hasText(className)) {
            return null;
        }
        try {
            ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
            return ClassUtils.forName(className, classLoader);
        } catch (ClassNotFoundException ex) {
            log.debug("[插件系统][路由]Failed to resolve bean class: {}", className, ex);
            return null;
        }
    }

    private void wrapWithRoutingFactoryBean(String beanName, BeanDefinition beanDefinition, Class<?> beanClass) {
        String originalClassName = beanDefinition.getBeanClassName();
        beanDefinition.setBeanClassName(GenericPluginRoutingFactoryBean.class.getName());
        MutablePropertyValues propertyValues = beanDefinition.getPropertyValues();
        propertyValues.add("beanName", beanName);
        propertyValues.add("delegateBeanName", beanName);
        propertyValues.add("targetType", beanClass);
        log.debug("[插件系统][路由]Wrapped bean '{}' of type '{}' with GenericPluginRoutingFactoryBean (originalClass={})", beanName, beanClass.getName(), originalClassName);
    }
}

