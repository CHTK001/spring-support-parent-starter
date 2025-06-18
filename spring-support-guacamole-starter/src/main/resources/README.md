# Spring Boot Guacamole Starter

这是一个基于Spring Boot 3.x的Apache Guacamole集成启动器，用于快速集成远程桌面功能。

## 功能特性

- 支持RDP、VNC、SSH等远程连接协议
- 提供WebSocket隧道实现实时远程桌面交互
- 支持键盘、鼠标事件传输
- 简单易用的配置方式
- 内置示例客户端页面

## 使用方法

### 1. 添加依赖

在你的项目的`pom.xml`中添加以下依赖：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-guacamole-starter</artifactId>
    <version>4.0.0</version>
</dependency>
```

### 2. 配置属性

在`application.yml`或`application.properties`中添加以下配置：

```yaml
spring:
  guacamole:
    enabled: true
    host: localhost  # Guacamole服务器地址
    port: 4822       # Guacamole服务器端口
    connect-timeout: 10000  # 连接超时时间(毫秒)
    read-timeout: 10000     # 读取超时时间(毫秒)
```

### 3. 安装Guacamole服务器

在使用此启动器之前，你需要安装并启动Guacamole服务器。可以参考[官方文档](https://guacamole.apache.org/doc/gug/installing-guacamole.html)
进行安装。

简要步骤：

1. 安装依赖库
2. 安装guacd服务
3. 启动guacd服务

### 4. 访问客户端页面

启动应用后，访问以下URL查看内置的远程桌面客户端页面：

```
http://localhost:8080/guacamole/client
```

## API接口

### 1. 获取连接配置

```
GET /guacamole/config?protocol=rdp&host=192.168.1.100&port=3389&username=admin&password=123456
```

### 2. WebSocket隧道

```
WebSocket: /guacamole/websocket-tunnel
```

### 3. 标准端点

```
WebSocket: /guacamole/tunnel/{protocol},{host},{port}[,{username}[,{password}]]
```

## 自定义开发

如果需要自定义远程桌面功能，可以注入`GuacamoleService`服务：

```java
@Autowired
private GuacamoleService guacamoleService;

// 创建RDP连接
GuacamoleTunnel tunnel = guacamoleService.createRdpTunnel(
    "192.168.1.100", 3389, "admin", "password", null, null, 1024, 768
);

// 创建VNC连接
GuacamoleTunnel tunnel = guacamoleService.createVncTunnel(
    "192.168.1.100", 5900, "password", 1024, 768
);

// 创建SSH连接
GuacamoleTunnel tunnel = guacamoleService.createSshTunnel(
    "192.168.1.100", 22, "admin", "password"
);
```

## 注意事项

1. 确保Guacamole服务器(guacd)已正确安装并运行
2. 远程主机需要开启相应的服务(RDP、VNC或SSH)
3. 网络环境需要确保连接畅通
4. 对于生产环境，建议配置适当的安全措施

## 依赖版本

- Spring Boot: 3.4.5
- Apache Guacamole: 1.5.5
- Java: 21 