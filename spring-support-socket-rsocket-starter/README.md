# Spring Support RSocket Starter

## 模块简介

`spring-support-rsocket-starter` 是一个基于 RSocket 的 Spring Boot 集成模块，提供了响应式双向通信的完整支持。本模块依赖 `utils-support-rsocket-starter`（底层协议实现），并提供了 Spring Boot 风格的自动配置、注解支持和会话管理功能。

## 架构设计

```
spring-support-rsocket-starter（Spring Boot 集成层）
    ↓ 依赖
utils-support-rsocket-starter（底层协议实现）
    ↓ 依赖
RSocket Core + Reactor（RSocket核心库）
```

## 主要特性

### 1. Spring Boot 自动配置
- ✅ 基于 `@ConfigurationProperties` 的配置管理
- ✅ 条件化 Bean 注册（`@ConditionalOnProperty`）
- ✅ 自动装配支持
- ✅ 配置元数据生成

### 2. 注解驱动开发
- ✅ `@OnConnect` - 连接事件处理
- ✅ `@OnDisconnect` - 断开连接事件处理
- ✅ `@OnEvent` - 自定义事件处理

### 3. 会话管理
- ✅ RSocketSession - 会话抽象
- ✅ RSocketUser - 用户信息管理
- ✅ RSocketSessionResolver - 会话解析器
- ✅ RSocketSessionTemplate - 操作模板

### 4. 认证与授权
- ✅ RSocketAuthFactory - 认证工厂接口
- ✅ 可插拔认证机制

### 5. 响应式特性
- ✅ 基于 Project Reactor 的响应式编程
- ✅ 背压（Backpressure）支持
- ✅ 流式数据处理
- ✅ 异步非阻塞

## 快速开始

### 1. 添加依赖

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-rsocket-starter</artifactId>
    <version>4.0.0.35</version>
</dependency>
```

### 2. 配置文件

在 `application.yml` 中配置：

```yaml
plugin:
  rsocket:
    enable: true                    # 启用RSocket
    host: 0.0.0.0                  # 监听地址
    port: 7000                      # 监听端口
    boss-count: 1                   # Boss线程数
    work-count: 100                 # Worker线程数
    max-frame-payload-length: 1048576  # 最大帧长度
    ping-timeout: 60000             # Ping超时时间（毫秒）
    ping-interval: 25000            # Ping间隔（毫秒）
    auth-factory: com.example.MyAuthFactory  # 认证工厂类
```

### 3. 使用注解处理事件

```java
package com.example.handler;

import com.chua.starter.rsocket.support.annotations.*;
import com.chua.starter.rsocket.support.session.RSocketSession;
import org.springframework.stereotype.Component;

/**
 * RSocket 事件处理器
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
@Component
public class RSocketEventHandler {
    
    /**
     * 客户端连接事件
     */
    @OnConnect
    public void onConnect(RSocketSession session) {
        System.out.println("客户端连接: " + session.getId());
        // 可以在这里进行认证、初始化等操作
    }
    
    /**
     * 客户端断开连接事件
     */
    @OnDisconnect
    public void onDisconnect(RSocketSession session) {
        System.out.println("客户端断开: " + session.getId());
        // 清理资源、记录日志等
    }
    
    /**
     * 处理自定义事件
     */
    @OnEvent("chat.message")
    public String onChatMessage(RSocketSession session, String message) {
        System.out.println("收到消息: " + message + " from " + session.getId());
        return "Echo: " + message;
    }
    
    /**
     * 处理用户登录事件
     */
    @OnEvent("user.login")
    public void onUserLogin(RSocketSession session, Map<String, Object> data) {
        String username = (String) data.get("username");
        session.setAttribute("username", username);
        System.out.println("用户登录: " + username);
    }
}
```

### 4. 使用 RSocketSessionTemplate

```java
package com.example.service;

import com.chua.starter.rsocket.support.session.RSocketSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * RSocket 消息服务
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
@Service
public class RSocketMessageService {
    
    @Autowired
    private RSocketSessionTemplate rsocketSessionTemplate;
    
    /**
     * 发送消息到指定会话
     */
    public void sendToSession(String sessionId, String event, Object data) {
        rsocketSessionTemplate.send(sessionId, event, data);
    }
    
    /**
     * 广播消息到所有会话
     */
    public void broadcast(String event, Object data) {
        rsocketSessionTemplate.broadcast(event, data);
    }
    
    /**
     * 发送消息到指定用户
     */
    public void sendToUser(String userId, String event, Object data) {
        rsocketSessionTemplate.sendToUser(userId, event, data);
    }
}
```

### 5. 自定义认证工厂

```java
package com.example.auth;

import com.chua.starter.rsocket.support.auth.RSocketAuthFactory;
import com.chua.starter.rsocket.support.session.RSocketSession;
import org.springframework.stereotype.Component;

/**
 * 自定义认证工厂
 * 
 * @author CH
 * @version 4.0.0.34
 * @since 2024/10/24
 */
@Component
public class MyAuthFactory implements RSocketAuthFactory {
    
    @Override
    public boolean authenticate(RSocketSession session, Map<String, Object> credentials) {
        // 实现认证逻辑
        String token = (String) credentials.get("token");
        
        if (validateToken(token)) {
            // 认证成功，设置用户信息
            session.setAttribute("userId", getUserIdFromToken(token));
            session.setAttribute("username", getUsernameFromToken(token));
            return true;
        }
        
        return false;
    }
    
    private boolean validateToken(String token) {
        // 验证token逻辑
        return token != null && !token.isEmpty();
    }
    
    private String getUserIdFromToken(String token) {
        // 从token中提取用户ID
        return "user-123";
    }
    
    private String getUsernameFromToken(String token) {
        // 从token中提取用户名
        return "admin";
    }
}
```

## 配置说明

### 完整配置示例

```yaml
plugin:
  rsocket:
    # 基础配置
    enable: true                      # 是否启用RSocket，默认false
    host: 0.0.0.0                    # 监听地址，默认0.0.0.0
    port: 7000                        # 监听端口，默认7000
    
    # 线程配置
    boss-count: 1                     # Boss线程数，默认1
    work-count: 100                   # Worker线程数，默认100
    use-linux-native-epoll: false    # 是否使用Epoll（Linux），默认false
    
    # 帧配置
    max-frame-size: 1048576            # 最大帧大小（字节），默认1MB（推荐使用）
    max-frame-payload-length: 1048576  # 最大帧载荷长度（字节），默认1MB（向后兼容）
    max-http-content-length: 1048576   # 最大HTTP内容长度（字节），默认1MB
    
    # 心跳配置
    ping-timeout: 60000               # Ping超时时间（毫秒），默认60秒
    ping-interval: 25000              # Ping间隔（毫秒），默认25秒
    
    # 认证配置
    auth-factory: com.example.MyAuthFactory  # 认证工厂类全限定名
    
    # 其他配置
    allow-custom-requests: true       # 是否允许自定义请求，默认true
    codec-type: json                  # 编解码器类型，默认json
```

## 与 Socket.IO 功能对比

本模块实现了 `spring-support-socketio-starter` 的所有核心功能：

| 功能 | Socket.IO | RSocket | 说明 |
|------|-----------|---------|------|
| 自动配置 | ✅ | ✅ | Spring Boot自动配置 |
| 注解支持 | ✅ (@OnConnect, @OnDisconnect, @OnEvent) | ✅ (相同注解) | 一致的API |
| 会话管理 | ✅ (SocketSession) | ✅ (RSocketSession) | 相同的抽象 |
| 用户管理 | ✅ (SocketUser) | ✅ (RSocketUser) | 用户信息封装 |
| 操作模板 | ✅ (SocketSessionTemplate) | ✅ (RSocketSessionTemplate) | 统一的操作接口 |
| 认证工厂 | ✅ (SocketAuthFactory) | ✅ (RSocketAuthFactory) | 可插拔认证 |
| 广播消息 | ✅ | ✅ | - |
| 点对点消息 | ✅ | ✅ | - |
| 房间管理 | ✅ | ❌ | RSocket使用路由替代 |
| 背压支持 | ❌ | ✅ | **RSocket独有** |
| 响应式流 | ❌ | ✅ | **RSocket独有** |
| 四种交互模式 | ❌ | ✅ | **RSocket独有** |

## 使用场景

### 适用场景
- ✅ 微服务之间的高性能通信
- ✅ 实时数据流传输
- ✅ 需要背压控制的场景
- ✅ 响应式系统架构
- ✅ 高并发实时通信

### 推荐使用
- 当需要比 Socket.IO 更高性能时
- 当需要背压机制时
- 当需要流式数据处理时
- 当使用响应式技术栈时

## 技术栈

- Spring Boot 3.4.5
- Utils Support RSocket Starter 4.0.0.31
- RSocket Core 1.1.4
- Project Reactor 3.6.2
- Java 21

## 示例代码

完整示例代码请参考：
- [服务器端示例](examples/server)
- [客户端示例](examples/client)
- [集成测试](src/test/java)

## 常见问题

### 1. 如何启用RSocket？

在配置文件中设置 `plugin.rsocket.enable=true`。

### 2. 如何修改端口？

在配置文件中设置 `plugin.rsocket.port=8000`。

### 3. 如何实现自定义认证？

实现 `RSocketAuthFactory` 接口并在配置中指定类名。

### 4. 如何获取当前会话？

在事件处理方法中注入 `RSocketSession` 参数。

### 5. 如何发送消息？

使用 `RSocketSessionTemplate` 的方法：
- `send(sessionId, event, data)` - 发送到指定会话
- `broadcast(event, data)` - 广播到所有会话
- `sendToUser(userId, event, data)` - 发送到指定用户

## 技术支持

- **版本**: 4.0.0.34
- **作者**: CH
- **日期**: 2024/10/24

## 更新日志

### v4.0.0.34 (2024-10-24)
- ✨ 初始版本发布
- ✨ 实现完整的Spring Boot集成
- ✨ 提供注解驱动的事件处理
- ✨ 实现会话管理和操作模板
- ✨ 支持自定义认证机制

## 许可证

本模块遵循项目主许可证。

