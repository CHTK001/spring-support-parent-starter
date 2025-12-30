# Spring Support Socket WebSocket Starter

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 📖 模块简介

Spring Support Socket WebSocket Starter 是基于 WebSocket 协议的实时通信模块，提供双向通信能力，适用于聊天、通知推送、实时数据展示等场景。

### ✨ 主要特性

- 🔄 **双向通信** - 服务器和客户端双向实时通信
- 🌐 **SockJS支持** - 兼容不支持WebSocket的浏览器
- 📡 **STOMP协议** - 支持STOMP消息协议
- 🔐 **安全认证** - 支持WebSocket连接认证
- 📊 **连接管理** - 会话管理和心跳检测

## 🚀 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-socket-websocket-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>
```

## ⚙️ 配置说明

| 参数名 | 类型 | 默认值 | 说明 |
|------|------|------|------|
| `plugin.websocket.enable` | Boolean | false | 是否启用WebSocket |
| `plugin.websocket.endpoint` | String | /ws | WebSocket端点 |
| `plugin.websocket.allowed-origins` | String | * | 允许的源 |
| `plugin.websocket.sockjs.enabled` | Boolean | true | 是否启用SockJS |

### 配置示例

```yaml
plugin:
  websocket:
    enable: true
    endpoint: /ws
    allowed-origins: "http://localhost:3000,https://example.com"
    sockjs:
      enabled: true
```

## 📝 使用示例

### WebSocket配置

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*")
                .withSockJS();
    }
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
```

### 服务端发送消息

```java
@Controller
public class WebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @MessageMapping("/chat.send")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(ChatMessage message) {
        return message;
    }
    
    public void pushNotification(String userId, Notification notification) {
        // 向特定用户推送消息
        messagingTemplate.convertAndSendToUser(
            userId, 
            "/queue/notifications", 
            notification
        );
    }
    
    public void broadcast(String message) {
        // 广播消息
        messagingTemplate.convertAndSend("/topic/public", message);
    }
}
```

### 客户端连接（JavaScript）

```javascript
// 使用SockJS和STOMP
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame);
    
    // 订阅公共主题
    stompClient.subscribe('/topic/public', function(message) {
        console.log('收到消息:', JSON.parse(message.body));
    });
    
    // 订阅私人队列
    stompClient.subscribe('/user/queue/notifications', function(message) {
        console.log('收到通知:', JSON.parse(message.body));
    });
    
    // 发送消息
    stompClient.send('/app/chat.send', {}, JSON.stringify({
        content: 'Hello World',
        sender: 'user1'
    }));
});
```

## 🔗 相关链接

- [返回Socket抽象模块](../spring-support-socket-starter/README.md)
- [返回主文档](../README.md)
- [Spring WebSocket文档](https://docs.spring.io/spring-framework/reference/web/websocket.html)

## 📄 许可证

本项目采用 [Apache License 2.0](../LICENSE) 许可证。
