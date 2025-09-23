package com.chua.starter.common.support.configuration;

import com.chua.starter.common.support.annotations.AutoController;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自动控制器注册器
 *
 * @author CH
 * @since 2025/9/8 10:47
 */
public class AutoControllerRegistrar implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        for (String beanName : registry.getBeanDefinitionNames()) {
            var bd = registry.getBeanDefinition(beanName);
            String clazzName = bd.getBeanClassName();
            if (clazzName == null) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(clazzName);
                for (Class<?> ifc : clazz.getInterfaces()) {
                    if (ifc.isAnnotationPresent(AutoController.class)) {
                        Class<?> controllerClazz = new ByteBuddy()
                                .subclass(clazz)
                                .annotateType(AnnotationDescription.Builder.ofType(RestController.class).build())
                                .make()
                                .load(clazz.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                                .getLoaded();
                        bd.setBeanClassName(controllerClazz.getName());
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }
}