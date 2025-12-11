# spring-support-report-client-arthas-starter

## 📖 模块简介

**Arthas 上报客户端模块** - 提供 Arthas 诊断工具的集成和上报功能，支持远程诊断和监控。

## ✨ 核心功能

### 🔍 Arthas 集成

- ✅ Arthas 自动启动
- ✅ Arthas Tunnel 连接
- ✅ 诊断信息上报
- ✅ 远程诊断支持

### 📊 监控上报

- ✅ JVM 信息上报
- ✅ 线程信息上报
- ✅ 内存信息上报
- ✅ 类加载信息上报

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-report-client-arthas-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置开关

```yaml
plugin:
  arthas:
    # 是否启用Arthas客户端
    # 默认: false
    enable: true

    # Arthas Tunnel 服务器地址
    tunnel-server: ws://localhost:7777/ws

    # 应用名称
    app-name: ${spring.application.name}

    # Arthas HTTP 端口
    http-port: 8563

    # Arthas Telnet 端口
    telnet-port: 3658
```

### 3. 启动应用

应用启动后，Arthas 会自动启动并连接到 Tunnel 服务器。

### 4. 远程诊断

通过监控平台的 Arthas 管理页面进行远程诊断。

## ⚙️ 配置说明

```yaml
plugin:
  arthas:
    enable: true

    # Tunnel 配置
    tunnel-server: ws://arthas-tunnel:7777/ws
    app-name: ${spring.application.name}

    # 端口配置
    http-port: 8563
    telnet-port: 3658

    # 自动启动
    auto-start: true

    # 诊断配置
    agent-id: ${spring.application.name}-${random.uuid}
```

## 💡 使用示例

### 远程执行命令

通过监控平台可以远程执行 Arthas 命令：

```bash
# 查看 JVM 信息
dashboard

# 查看线程信息
thread

# 反编译类
jad com.example.UserService

# 监控方法调用
watch com.example.UserService getUserById '{params, returnObj}'
```

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

---

**作者**: CH  
**版本**: 4.0.0.32  
**更新时间**: 2024/12/11
