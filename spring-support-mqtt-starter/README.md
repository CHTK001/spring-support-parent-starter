# spring-support-mqtt-starter

## 📖 模块简介

**MQTT 消息队列模块** - 提供 MQTT 协议的消息发布和订阅功能，支持 QoS、持久化、遗嘱消息等 MQTT 特性。

## ✨ 核心功能

### 📨 消息发布

- ✅ 主题发布
- ✅ QoS 级别控制
- ✅ 消息持久化
- ✅ 遗嘱消息

### 📬 消息订阅

- ✅ 主题订阅
- ✅ 通配符订阅
- ✅ 消息监听
- ✅ 自动重连

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-mqtt-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置开关

**配置文件**：`application.yml`

```yaml
plugin:
  mqtt:
    # 是否启用MQTT功能
    # 默认: false
    # 说明: 设置为true时才会启用MQTT客户端
    enable: true

    # MQTT Broker 地址
    broker-url: tcp://localhost:1883

    # 客户端ID
    client-id: ${spring.application.name}-${random.uuid}

    # 用户名
    username: admin

    # 密码
    password: admin

    # 自动重连
    auto-reconnect: true
```

### 3. 发布消息

```java
@Autowired
private MqttService mqttService;

// 发布消息
mqttService.publish("topic/test", "Hello MQTT");

// 指定 QoS 发布
mqttService.publish("topic/test", "Hello MQTT", 1);

// 持久化消息
mqttService.publish("topic/test", "Hello MQTT", 1, true);
```

### 4. 订阅消息

```java
@Component
public class MqttMessageListener {

    @MqttSubscribe(topic = "topic/test")
    public void onMessage(String topic, String message) {
        log.info("收到消息: topic={}, message={}", topic, message);
    }

    // 通配符订阅
    @MqttSubscribe(topic = "topic/#")
    public void onWildcardMessage(String topic, String message) {
        log.info("通配符消息: topic={}, message={}", topic, message);
    }
}
```

## ⚙️ 配置说明

### 完整配置示例

```yaml
plugin:
  mqtt:
    # 功能开关
    enable: true

    # Broker 配置
    broker-url: tcp://localhost:1883
    client-id: ${spring.application.name}-${random.uuid}
    username: admin
    password: admin

    # 连接配置
    connection-timeout: 30
    keep-alive-interval: 60
    auto-reconnect: true
    max-reconnect-delay: 128000

    # 遗嘱消息
    will:
      topic: client/offline
      message: ${spring.application.name} offline
      qos: 1
      retained: true

    # 订阅配置
    subscriptions:
      - topic: topic/test
        qos: 1
      - topic: topic/monitor/#
        qos: 0
```

### QoS 级别说明

- **QoS 0** - 最多一次，消息可能丢失
- **QoS 1** - 至少一次，消息可能重复
- **QoS 2** - 恰好一次，消息不丢失不重复（性能较低）

## 💡 使用示例

### 发布消息

```java
@Service
public class DeviceService {

    @Autowired
    private MqttService mqttService;

    public void sendDeviceData(String deviceId, DeviceData data) {
        String topic = "device/" + deviceId + "/data";
        String message = JSON.toJSONString(data);

        // QoS 1 确保消息送达
        mqttService.publish(topic, message, 1);
    }
}
```

### 订阅消息

```java
@Component
public class DeviceMessageHandler {

    // 订阅单个主题
    @MqttSubscribe(topic = "device/+/data", qos = 1)
    public void handleDeviceData(String topic, String message) {
        String deviceId = extractDeviceId(topic);
        DeviceData data = JSON.parseObject(message, DeviceData.class);

        // 处理设备数据
        processDeviceData(deviceId, data);
    }

    // 订阅多级通配符
    @MqttSubscribe(topic = "device/#", qos = 0)
    public void handleAllDeviceMessages(String topic, String message) {
        log.info("设备消息: topic={}, message={}", topic, message);
    }
}
```

### 遗嘱消息

```java
@Configuration
public class MqttConfig {

    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();

        // 设置遗嘱消息（客户端异常断开时发送）
        options.setWill(
            "client/status",           // 主题
            "offline".getBytes(),      // 消息
            1,                         // QoS
            true                       // 保留消息
        );

        return options;
    }
}
```

## 🎯 设计原则

### 1. 可靠性

- ✅ 自动重连机制
- ✅ 消息持久化
- ✅ QoS 保证

### 2. 易用性

- ✅ 注解式订阅
- ✅ 简洁的 API
- ✅ 自动配置

### 3. 高性能

- ✅ 异步发送
- ✅ 连接池管理
- ✅ 批量处理

## 🔗 相关模块

- [spring-support-common-starter](../spring-support-common-starter) - 公共基础模块

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

---

**作者**: CH  
**版本**: 4.0.0.32  
**更新时间**: 2024/12/11
