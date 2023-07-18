package com.chua.starter.mqtt.support.callback;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * @author CH
 */
@Slf4j
public class DelegateCallback implements MqttCallback {
    @Override
    public void connectionLost(Throwable cause) {
        log.warn("连接丢失: {}", cause.getLocalizedMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        log.warn("监听到消息: {}", topic);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }
}
