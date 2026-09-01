# Spring Support OSGi Starter

基于 Apache Felix 的 OSGi 框架集成模块，为 Spring Boot 应用提供完整的 OSGi 运行时支持，包括 Gogo shell（Telnet）和 Apache SSHD（SSH）远程管理。

## 架构设计

```
┌──────────────────────────────────────────────────────┐
│              Spring Boot Application                  │
│                                                       │
│  ┌─────────────────────────────────────────────┐     │
│  │         OsgiAutoConfiguration                │     │
│  │  - 启动 Felix 框架                           │     │
│  │  - 自动安装 Gogo/SSHD bundle                 │     │
│  │  - 桥接 OSGi 服务到 ObjectContext            │     │
│  └──────────────────┬──────────────────────────┘     │
│                     │                                 │
│                     ▼                                 │
│  ┌─────────────────────────────────────────────┐     │
│  │           FelixOsgiLauncher                  │     │
│  │  org.apache.felix.framework 7.0.5            │     │
│  │  org.apache.felix.gogo.* (runtime/cmd/shell) │     │
│  │  org.apache.sshd.sshd-osgi 2.13.2            │     │
│  └──────────────────┬──────────────────────────┘     │
│                     │                                 │
│         ┌───────────┴───────────┐                    │
│         ▼                       ▼                    │
│   SSH :8122              Telnet :6666                │
│   (默认，优先)             (回退)                      │
│         │                       │                    │
│         ▼                       ▼                    │
│  OsgiBeanDefinitionRegister ──▶ ObjectContext         │
└──────────────────────────────────────────────────────┘
```

## 功能特性

### 核心能力
- **Felix OSGi 框架** — Apache Felix 7.0.5，bundle 全生命周期管理
- **Gogo Shell** — runtime/command/shell 三件套，自动安装
- **Apache SSHD** — sshd-osgi + sshd-sftp + sshd-scp，SSH 远程管理（优先）
- **协议选择** — SSH 优先，Telnet 自动回退
- **服务桥接** — OSGi 服务通过 ObjectContext 跨容器查询

### 单一协议原则
Shell 只允许启动一个远程协议：
- `protocol=ssh`（默认）→ SSHD bundle，端口 8122
- `protocol=telnet` → Gogo telnet，端口 6666

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-osgi-starter</artifactId>
    <version>4.0.0.43</version>
</dependency>
```

### 2. 配置文件

**SSH 远程管理（默认，推荐）：**
```yaml
plugin:
  osgi:
    enabled: true
    storage: ./osgi-cache
    start-level: 4
    shell:
      enabled: true
      protocol: ssh          # SSH 优先
      port: 8122
      host: 0.0.0.0
      username: admin
      password: your-password
```

**Telnet 远程管理（回退模式）：**
```yaml
plugin:
  osgi:
    shell:
      enabled: true
      protocol: telnet
      port: 6666
      host: 127.0.0.1
```

### 3. 连接测试

```bash
# SSH 连接
ssh admin@localhost -p 8122

# Telnet 连接（回退）
telnet localhost 6666
```

## 配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `plugin.osgi.enabled` | true | 是否启用 OSGi |
| `plugin.osgi.storage` | ./osgi-cache | Felix 持久化目录 |
| `plugin.osgi.start-level` | 4 | 初始启动级别 |
| `plugin.osgi.shell.enabled` | false | 是否启用远程管理 |
| `plugin.osgi.shell.protocol` | ssh | 协议类型：ssh 或 telnet |
| `plugin.osgi.shell.port` | 0 | 端口（0=协议默认：SSH=8122, Telnet=6666） |
| `plugin.osgi.shell.host` | 127.0.0.1 | 绑定地址 |
| `plugin.osgi.shell.username` | "" | 认证用户名 |
| `plugin.osgi.shell.password` | "" | 认证密码 |

## API 使用

```java
@Autowired
private FelixOsgiLauncher osgiLauncher;

// 检查状态
boolean active = osgiLauncher.isActive();

// 查询 bundle
List<OsgiBundle> bundles = osgiLauncher.getBundles();

// 安装/卸载 bundle
osgiLauncher.installBundle("mvn:group/artifact/version");
osgiLauncher.uninstallBundle("org.example.my-bundle");

// 查询 OSGi 服务
MyService svc = osgiLauncher.getService(MyService.class);

// ObjectContext 桥接
MyService svc = objectContext.getBeanOfType(MyService.class);
```

## 版本历史

- **4.0.0.43** — 内聚 Gogo+SSHD 到 utils-support-osgi-starter，简化 Shell 配置（单一协议），删除 install-gogo/install-sshd 标志
- **4.0.0.42** — 初始版本
