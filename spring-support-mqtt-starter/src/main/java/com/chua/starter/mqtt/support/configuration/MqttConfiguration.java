package com.chua.starter.mqtt.support.configuration;

import com.chua.common.support.log.Log;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.mqtt.support.annotation.Mqtt;
import com.chua.starter.mqtt.support.properties.MqttProperties;
import com.chua.starter.mqtt.support.template.MqttTemplate;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ReflectionUtils;

/**
 * mqtt
 * @author CH
 */
@EnableConfigurationProperties(MqttProperties.class)
public class MqttConfiguration implements ApplicationContextAware, SmartInstantiationAwareBeanPostProcessor {

    @Autowired
    private MqttProperties mqttProperties;

    private static final Log log = Log.getLogger(MqttConfiguration.class);

    private MqttTemplate mqttTemplate ;

    @Bean("mqttClient")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("!T(org.springframework.util.StringUtils).isEmpty('${plugin.spring.mqtt.address:}')")
    public MqttTemplate mqttClient() {
        return mqttTemplate;
    }

    @Override
    public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
        if (null != mqttTemplate) {
            Class<?> aClass = bean.getClass();
            ReflectionUtils.doWithMethods(aClass, method -> {
                if (method.isAnnotationPresent(Mqtt.class)) {
                    mqttTemplate.register(method, bean);
                }
            });
        }
        return SmartInstantiationAwareBeanPostProcessor.super.postProcessAfterInstantiation(bean, beanName);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.mqttProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(MqttProperties.PRE, MqttProperties.class);
        if(StringUtils.isNotEmpty(mqttProperties.getAddress())) {
            try {
                this.mqttTemplate = new MqttTemplate(mqttProperties);
            } catch (MqttException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
