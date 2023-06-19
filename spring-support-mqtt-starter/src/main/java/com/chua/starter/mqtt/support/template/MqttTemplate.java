package com.chua.starter.mqtt.support.template;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.mqtt.support.callback.DelegateCallback;
import com.chua.starter.mqtt.support.properties.MqttProperties;
import com.chua.starter.mqtt.support.status.MqttTask;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * DelegateMqttClient
 *
 * @author CH
 */
public class MqttTemplate extends MqttClient implements DisposableBean, InitializingBean {

    private final MqttConnectOptions mqttConnectOptions;
    private final MqttProperties mqttProperties;
    @Resource
    private MqttCallback mqttCallback;

    public MqttTemplate(MqttProperties mqttProperties) throws MqttException {
        super(mqttProperties.getHost(), mqttProperties.getClientId());
        this.mqttConnectOptions = mqttConnectOptions(mqttProperties);
        this.mqttProperties = mqttProperties;
    }

    /**
     * 订阅
     *
     * @param topic topic
     * @param qos   qos
     */
    public void subscribe(String topic, int qos) throws MqttException {
        super.subscribe(topic, qos);
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
     * @param qos      qos
     * @param retained 留存
     * @return task
     */
    public MqttTask publish(String msg, String topic, int qos, boolean retained) throws Exception {
        MqttMessage message = new MqttMessage();
        message.setPayload(StringUtils.utf8Bytes(msg));
        message.setQos(qos);
        message.setRetained(retained);
        MqttTopic mqttTopic = this.getTopic(topic);
        return new MqttTask(mqttTopic.publish(message));
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
        options.setPassword(mqttProperties.getPassword().toCharArray());
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
        mqttCallback = mqttCallback == null ? new DelegateCallback() : mqttCallback;
        this.setCallback(mqttCallback);
        this.connect(mqttConnectOptions);
        int size = mqttProperties.getTopic().size();
        int qos = mqttProperties.getQos();
        int[] ints = new int[size];
        AtomicInteger index = new AtomicInteger(0);
        IntStream.range(0, size).forEach(it -> {
            ints[index.getAndIncrement()] = qos;
        });
        this.subscribe(mqttProperties.getTopic().toArray(new String[0]), ints);
    }
}
