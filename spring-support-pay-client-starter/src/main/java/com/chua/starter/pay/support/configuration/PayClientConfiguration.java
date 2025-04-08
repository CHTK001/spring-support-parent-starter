package com.chua.starter.pay.support.configuration;

import com.chua.starter.mqtt.support.template.MqttTemplate;
import com.chua.starter.pay.support.annotations.OnPayListener;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ReflectionUtils;

/**
 * 客戶端配置
 *
 * @author CH
 * @since 2025/1/3
 */
public class PayClientConfiguration  {

    public static PayListenerService factory = new PayListenerService();



    @Bean
    public PayListenerService payListenerService(@Autowired(required = false) MqttTemplate mqttTemplate) {
        factory.register(mqttTemplate);
        return factory;
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
