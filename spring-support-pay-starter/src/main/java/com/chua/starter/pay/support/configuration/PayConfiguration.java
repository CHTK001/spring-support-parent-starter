package com.chua.starter.pay.support.configuration;

import com.chua.starter.pay.support.annotations.OnPayListener;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.ReflectionUtils;

/**
 * 支付配置
 * @author CH
 * @since 2024/12/27
 */
@Slf4j
@MapperScan("com.chua.starter.pay.support.mapper")
@ComponentScan({
        "com.chua.starter.pay.support.service",
        "com.chua.starter.pay.support.controller",
        "com.chua.starter.pay.support.scheduler",
})
@EnableScheduling
public class PayConfiguration implements BeanDefinitionRegistryPostProcessor {

    final PayListenerService factory = new PayListenerService();

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        registry.registerBeanDefinition("paySmartInstantiationAwareBeanPostProcessor",
                        BeanDefinitionBuilder.genericBeanDefinition(PayListenerService.class, () -> factory)
                .getBeanDefinition());
    }


    @Bean
    public PaySmartInstantiationAwareBeanPostProcessor getPaySmartInstantiationAwareBeanPostProcessor() {
        return new PaySmartInstantiationAwareBeanPostProcessor();
    }

    public class PaySmartInstantiationAwareBeanPostProcessor implements SmartInstantiationAwareBeanPostProcessor {
        @Override
        public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
            Class<?> aClass = bean.getClass();
            ReflectionUtils.doWithMethods(aClass, method -> {
                if(method.isAnnotationPresent(OnPayListener.class)) {
                    factory.addListener(method.getAnnotation(OnPayListener.class), bean, method);
                }
            });
            return SmartInstantiationAwareBeanPostProcessor.super.postProcessAfterInstantiation(bean, beanName);
        }

    }
}
