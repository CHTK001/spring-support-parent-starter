package com.chua.starter.queue.mqtt;

import com.chua.starter.queue.MessageTemplate;
import com.chua.starter.queue.properties.QueueProperties;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QueueProperties.class)
public class MqttQueueAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MqttClient mqttClient(QueueProperties properties) throws Exception {
        QueueProperties.MqttConfig conf = properties.getMqtt();
        String clientId = conf.getClientId() == null || conf.getClientId().isEmpty() ? "spring-support-" + System.currentTimeMillis() : conf.getClientId();
        MqttClient client = new MqttClient(conf.getAddress(), clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(conf.getConnectionTimeout());
        options.setKeepAliveInterval(conf.getKeepAliveInterval());
        if (conf.getUsername() != null) {
            options.setUserName(conf.getUsername());
        }
        if (conf.getPassword() != null) {
            options.setPassword(conf.getPassword().toCharArray());
        }
        client.connect(options);
        return client;
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageTemplate mqttMessageTemplate(MqttClient client, QueueProperties properties) {
        return new MqttMessageTemplate(client, properties);
    }
}
