# Spring Support Queue MQTT Starter

[![Maven Central](https://img.shields.io/maven-central/v/com.chua/spring-support-queue-mqtt-starter.svg)](https://search.maven.org/artifact/com.chua/spring-support-queue-mqtt-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 📖 模块简介

Spring Support Queue MQTT Starter 是基于 MQTT 协议的消息队列实现模块，专为物联网(IoT)场景设计，提供轻量级、低带宽的消息传输。

### ✨ 主要特性

- 🌐 **物联网协议** - MQTT 3.1.1/5.0协议支持
- 📶 **QoS保证** - 支持三种服务质量级别
- 🔄 **主题订阅** - 支持通配符主题订阅
- 💾 **持久会话** - 支持客户端会话持久化
- 🔒 **安全传输** - 支持TLS/SSL加密传输
- ⚡ **轻量高效** - 适合低带宽、高延迟网络

## 🚀 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-queue-mqtt-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>
```

## ⚙️ 配置说明

| 参数名 | 类型 | 默认值 | 说明 |
|------|------|------|------|
| `plugin.mqtt.enable` | Boolean | false | 是否启用MQTT |
| `plugin.mqtt.broker-url` | String | tcp://localhost:1883 | MQTT服务器地址 |
| `plugin.mqtt.client-id` | String | mqtt-client-${random.value} | 客户端ID |
| `plugin.mqtt.username` | String | - | 用户名 |
| `plugin.mqtt.password` | String | - | 密码 |
| `plugin.mqtt.qos` | Integer | 1 | 服务质量等级(0,1,2) |
| `plugin.mqtt.retained` | Boolean | false | 是否保留消息 |

### 配置示例

```yaml
plugin:
  mqtt:
    enable: true
    broker-url: tcp://mqtt.example.com:1883
    client-id: iot-device-001
    username: mqtt_user
    password: mqtt_pass
    qos: 1  # QoS 1: 至少一次
    retained: false
```

## 📝 使用示例

### 发布消息

```java
@Service
public class MqttPublisher {

    @Autowired
    private MqttTemplate mqttTemplate;
    
    public void publishSensorData(String topic, SensorData data) {
        // 发布消息 QoS 1
        mqttTemplate.publish(topic, data, 1, false);
    }
    
    public void publishRetainedMessage(String topic, String message) {
        // 发布保留消息
        mqttTemplate.publish(topic, message, 1, true);
    }
}
```

### 订阅消息

```java
@Component
public class MqttSubscriber {

    @MqttListener(topics = "sensor/+/temperature")
    public void handleTemperature(String topic, String payload) {
        log.info("温度数据: topic={}, data={}", topic, payload);
    }
    
    @MqttListener(topics = {"device/#"})
    public void handleDeviceMessage(MqttMessage message) {
        log.info("设备消息: qos={}, payload={}", 
                 message.getQos(), new String(message.getPayload()));
    }
}
```

## 🔗 相关链接

- [返回消息队列抽象模块](../spring-support-queue-starter/README.md)
- [返回主文档](../README.md)
- [MQTT官方文档](https://mqtt.org/)

## 📄 许可证

本项目采用 [Apache License 2.0](../LICENSE) 许可证。
