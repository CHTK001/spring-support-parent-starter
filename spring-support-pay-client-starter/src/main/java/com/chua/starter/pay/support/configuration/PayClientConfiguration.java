package com.chua.starter.pay.support.configuration;

import com.chua.common.support.protocol.ClientSetting;
import com.chua.common.support.utils.ClassUtils;
import com.chua.mica.support.client.MicaClient;
import com.chua.mica.support.client.session.MicaSession;
import com.chua.starter.pay.support.annotations.OnPayListener;
import com.chua.starter.pay.support.properties.PayNotifyProperties;
import com.chua.starter.pay.support.properties.PayProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * 客戶端配置
 *
 * @author CH
 * @since 2025/1/3
 */
@EnableConfigurationProperties({PayNotifyProperties.class, PayProperties.class})
public class PayClientConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    public static PayListenerService factory = new PayListenerService();
    private PayNotifyProperties payNotifyProperties;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        registry.registerBeanDefinition("paySmartInstantiationAwareBeanPostProcessor",
                BeanDefinitionBuilder.genericBeanDefinition(PayListenerService.class, () -> factory)
                        .getBeanDefinition());
        if(!payNotifyProperties.isEnable()) {
            return;
        }

        if(ClassUtils.isPresent("com.chua.starter.pay.support.configuration.PayConfiguration")) {
            return;
        }

        if(payNotifyProperties.getType() == PayNotifyProperties.Type.MQTT) {
            registerMqttClient(registry, payNotifyProperties);
        }

    }

    private void registerMqttClient(BeanDefinitionRegistry registry, PayNotifyProperties payNotifyProperties) {
        PayNotifyProperties.MqttConfig mqttConfig = payNotifyProperties.getMqttConfig();

        if(!StringUtils.hasLength(mqttConfig.getHost())) {
            return;
        }

        MicaClient micaClient = new MicaClient(ClientSetting.builder()
                .host(mqttConfig.getHost())
                .port(mqttConfig.getPort())
                .clientId(mqttConfig.getClientId())
                .username(mqttConfig.getUsername())
                .password(mqttConfig.getPassword())
                .build());

        micaClient.connect();
        MicaSession session = (MicaSession) micaClient.createSession("default");
        factory.register(session);

        registry.registerBeanDefinition("MicaClient",
                BeanDefinitionBuilder.genericBeanDefinition(MicaClient.class, () -> micaClient)
                        .setDestroyMethodName("close")
                        .getBeanDefinition());

    }


    @Bean
    public PaySmartInstantiationAwareBeanPostProcessor getPaySmartInstantiationAwareBeanPostProcessor() {
        return new PaySmartInstantiationAwareBeanPostProcessor();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.payNotifyProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(PayNotifyProperties.PRE, PayNotifyProperties.class);
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
