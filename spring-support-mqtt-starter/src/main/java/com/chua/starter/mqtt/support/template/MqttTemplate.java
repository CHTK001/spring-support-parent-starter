package com.chua.starter.mqtt.support.template;

import com.chua.common.support.utils.ClassUtils;
import com.chua.common.support.utils.IdUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.configuration.SpringBeanUtils;
import com.chua.starter.mqtt.support.annotation.Mqtt;
import com.chua.starter.mqtt.support.callback.DelegateCallback;
import com.chua.starter.mqtt.support.properties.MqttProperties;
import com.chua.starter.mqtt.support.status.MqttTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DelegateMqttClient
 *
 * @author CH
 */
@Slf4j
public class MqttTemplate extends MqttClient implements DisposableBean, InitializingBean {

    private final MqttConnectOptions mqttConnectOptions;
    private final MqttProperties mqttProperties;

    private final DelegateCallback delegateCallback = new DelegateCallback();

    private final Map<String, List<MqttBean>> beans = new ConcurrentHashMap<>();


    public MqttTemplate(MqttProperties mqttProperties) throws MqttException {
        super(mqttProperties.getHost(), StringUtils.ifValid(mqttProperties.getClientId(), IdUtils.createTid()));
        this.mqttConnectOptions = mqttConnectOptions(mqttProperties);
        this.mqttProperties = mqttProperties;
        log.info(">>>> MQTT服务器[{}]连接成功", mqttProperties.getHost());
    }

    /**
     * 取消订阅
     *
     * @param topic topic
     */
    public void unsubscribe(String topic) throws MqttException {
        super.unsubscribe(topic);
    }
    /**
     * 发布消息
     *
     * @param msg      消息
     * @param topic    topic
     * @return task
     */
    public MqttTask publish(String topic, String msg) {
        MqttMessage message = new MqttMessage();
        message.setPayload(StringUtils.utf8Bytes(msg));
        message.setQos(0);
        message.setRetained(false);
        MqttTopic mqttTopic = this.getTopic(topic);
        try {
            return new MqttTask(mqttTopic.publish(message));
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * 发布消息
     *
     * @param msg      消息
     * @param topic    topic
     * @param qos      qos
     * @param retained 留存
     * @return task
     */
    public MqttTask publish(String topic, String msg, int qos, boolean retained) {
        MqttMessage message = new MqttMessage();
        message.setPayload(StringUtils.utf8Bytes(msg));
        message.setQos(qos);
        message.setRetained(retained);
        MqttTopic mqttTopic = this.getTopic(topic);
        try {
            return new MqttTask(mqttTopic.publish(message));
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 设置mqtt连接参数
     *
     * @param mqttProperties mqttProperties
     * @return MqttConnectOptions
     */
    public MqttConnectOptions mqttConnectOptions(MqttProperties mqttProperties) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(mqttProperties.getName());
        if(!StringUtils.isEmpty(mqttProperties.getPassword())) {
            options.setPassword(mqttProperties.getPassword().toCharArray());
        }
        options.setConnectionTimeout(mqttProperties.getTimeout());
        options.setKeepAliveInterval(mqttProperties.getKeepalive());
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        return options;
    }

    @Override
    public void destroy() throws Exception {
        super.disconnect();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.setCallback(delegateCallback);
        this.connect(mqttConnectOptions);
        this.register();
    }

    private void register() {
        for (Map.Entry<String, List<MqttBean>> entry : beans.entrySet()) {
            List<MqttBean> value = entry.getValue();
            for (MqttBean mqttBean : value) {
                try {
                    super.subscribe(entry.getKey(), mqttBean.mqtt.qos(), mqttBean.mqttMessageListener);
                } catch (MqttException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void register(Method method, Object bean) {
        Mqtt mqtt = method.getDeclaredAnnotation(Mqtt.class);
        ClassUtils.setAccessible(method);
        String[] topic = mqtt.topic();
        IMqttMessageListener mqttMessageListener = (topic1, message) -> {
            try {
                method.invoke(bean, createArgs(method, topic1, message));
            } catch (Throwable ignored) {
            }
        };
        for (String s : topic) {
            beans.computeIfAbsent(s, it -> new LinkedList<>())
                    .add(new MqttBean(method, bean, mqtt, mqttMessageListener));
        }
    }

    private Object[] createArgs(Method method, String topic, MqttMessage message) {
        Parameter[] parameters = method.getParameters();
        Object[] value = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Class<?> type = parameter.getType();

            Object item = getQualifier(type);
            if(null != item) {
                value[i] = item;
                continue;
            }

            if(isTopic(parameter)) {
                value[i] = topic;
                continue;
            }

            if(type.isAssignableFrom(MqttMessage.class)) {
                value[i] = message;
                continue;
            }
        }
        return value;
    }

    private boolean isTopic(Parameter parameter) {
        return "topic".equals(parameter.getName());
    }

    private Object getQualifier(Class<?> type) {
        if(!type.isAnnotationPresent(Qualifier.class)) {
            return null;
        }

        Qualifier qualifier = type.getDeclaredAnnotation(Qualifier.class);
        if(StringUtils.isNotEmpty(qualifier.value())) {
            return SpringBeanUtils.getApplicationContext().getBean(qualifier.value(), type);
        }

        return null;
    }

    @Data
    @AllArgsConstructor
    static class MqttBean {

        private Method method;
        private Object bean;

        private Mqtt mqtt;

        private IMqttMessageListener mqttMessageListener;
    }
}
