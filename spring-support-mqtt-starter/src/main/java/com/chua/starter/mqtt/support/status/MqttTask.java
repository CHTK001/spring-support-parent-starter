package com.chua.starter.mqtt.support.status;

import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.concurrent.TimeUnit;

/**
 * 任务
 *
 * @author CH
 */
public class MqttTask {
    private final MqttDeliveryToken token;

    public MqttTask(MqttDeliveryToken token) {

        this.token = token;
    }

    public void waitForCompletion() throws MqttException {
        token.waitForCompletion();
    }

    public void waitForCompletion(int timeout, TimeUnit timeUnit) throws MqttException {
        token.waitForCompletion(timeUnit.toMillis(timeout));
    }
}
