package com.chua.starter.mqtt.support.configuration;

import com.chua.common.support.log.Log;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.mqtt.support.template.MqttTemplate;
import com.chua.starter.mqtt.support.properties.MqttProperties;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;

import javax.annotation.Resource;

/**
 * mqtt
 * @author CH
 */
@EnableConfigurationProperties(MqttProperties.class)
public class MqttConfiguration implements ApplicationContextAware {

    @Resource
    private MqttProperties mqttProperties;

    private static final Log log = Log.getLogger(MqttConfiguration.class);


    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = MqttProperties.PRE, name = "topic")
    public MqttTemplate mqttClient(MqttProperties mqttProperties) {
        if(CollectionUtils.isEmpty(mqttProperties.getTopic())) {
            throw new RuntimeException("请配置MQTT的topic");
        }
        try {
            return new MqttTemplate(mqttProperties);
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.mqttProperties = Binder.get(applicationContext.getEnvironment()).bindOrCreate(MqttProperties.PRE, MqttProperties.class);
    }
}
