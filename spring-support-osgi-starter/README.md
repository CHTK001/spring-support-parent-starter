# Spring Support OSGi Starter

基于 Apache Felix 的 OSGi 框架集成模块，为 Spring Boot 应用提供完整的 OSGi 运行时支持，包括 Gogo shell（Telnet）和 Apache SSHD（SSH）远程管理。

## 功能特性

### 核心能力
- **Felix OSGi 框架集成** — 启动 Apache Felix 7.x，管理 bundle 生命周期
- **Gogo Shell 远程管理** — 通过 Telnet（端口 6666）访问 OSGi 命令行
- **Apache SSHD OSGi Bundle** — 通过 SSH（端口 8122）提供安全的远程管理
- **Bundle 在线安装/卸载** — 支持 Maven 坐标自动下载和安装
- **版本升级优先** — 自动比较版本号，高版本优先替换旧 bundle
- **OSGi 服务桥接** — 通过 `ObjectContext` 跨容器查询 OSGi 注册的服务

### 安全特性
- IP 访问控制（白名单/黑名单）
- 密码认证 + 公钥认证
- 会话超时和空闲超时
- 登录失败锁定机制

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

在 `application.yml` 中添加：

```yaml
plugin:
  osgi:
    enabled: true                    # 启用 OSGi
    storage: ./osgi-cache            # Felix 持久化目录
    start-level: 4                   # 初始启动级别

    # Gogo shell 远程管理
    shell:
      enabled: true                  # 启用 Gogo shell
      telnet-port: 6666              # Telnet 端口
      ssh-port: 0                    # Gogo SSH（默认关闭，使用独立 SSHD bundle）
      host: 127.0.0.1

    # SSHD Bundle 安装配置
    shell-bundles:
      enabled: true                  # 启用 Bundle 管理器
      install-gogo: true             # 自动安装 Gogo bundle
      install-sshd: true             # 自动安装 Apache SSHD bundle
      sshd-port: 8122                # SSHD 监听端口
      sshd-host: 127.0.0.1
      sshd-username: admin
      sshd-password: admin123
      sftp-enabled: true             # 启用 SFTP
```

### 3. 启动应用

启动 Spring Boot 应用后，OSGi 框架将自动启动：

```
[OSGi] Felix 框架已启动，OSGi 集成就绪
[OSGi] Shell Bundle 管理器已初始化，Gogo=true, SSHD=true
[OSGi-Shell] Bundle 已安装: org.apache.felix.gogo.runtime (1.1.6)
[OSGi-Shell] Bundle 已安装: org.apache.felix.gogo.command (1.1.2)
[OSGi-Shell] Bundle 已安装: org.apache.felix.gogo.shell (1.1.4)
```

### 4. 连接测试

```bash
# Telnet 连接（Gogo shell）
telnet localhost 6666

# SSH 连接（SSHD bundle）
ssh admin@localhost -p 8122

# SFTP 文件传输
sftp -P 8122 admin@localhost
```

## 配置说明

### plugin.osgi 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `plugin.osgi.enabled` | true | 是否启用 OSGi 集成 |
| `plugin.osgi.storage` | ./osgi-cache | Felix 持久化存储目录 |
| `plugin.osgi.start-level` | 4 | 初始启动级别 |

### plugin.osgi.shell 配置项（Gogo Telnet/SSH）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `plugin.osgi.shell.enabled` | false | 是否启用 Gogo shell 远程管理 |
| `plugin.osgi.shell.telnet-port` | 6666 | Telnet 监听端口（0 表示关闭） |
| `plugin.osgi.shell.ssh-port` | 0 | Gogo SSH 端口（0 表示关闭） |
| `plugin.osgi.shell.host` | 127.0.0.1 | 绑定地址 |
| `plugin.osgi.shell.ssh-enabled` | false | 是否启用 Gogo SSH（兼容旧配置） |
| `plugin.osgi.shell.username` | "" | SSH 默认用户名 |
| `plugin.osgi.shell.password` | "" | SSH 默认密码 |
| `plugin.osgi.shell.ssh-keys` | - | SSH 主机密钥文件路径 |

### plugin.osgi.shell-bundles 配置项（SSHD Bundle 管理）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `plugin.osgi.shell-bundles.enabled` | true | 是否启用 Bundle 管理器 |
| `plugin.osgi.shell-bundles.install-gogo` | true | 是否自动安装 Gogo bundle |
| `plugin.osgi.shell-bundles.install-sshd` | true | 是否自动安装 SSHD bundle |
| `plugin.osgi.shell-bundles.sshd-port` | 8122 | SSHD 监听端口 |
| `plugin.osgi.shell-bundles.sshd-host` | 127.0.0.1 | SSHD 绑定地址 |
| `plugin.osgi.shell-bundles.sshd-username` | admin | SSHD 用户名 |
| `plugin.osgi.shell-bundles.sshd-password` | "" | SSHD 密码 |
| `plugin.osgi.shell-bundles.sftp-enabled` | true | 是否启用 SFTP |
| `plugin.osgi.shell-bundles.allow-root-login` | false | 是否允许 root 登录 |

## API 使用

### 编程式 Bundle 管理

```java
@Autowired
private OsgiShellBundleManager bundleManager;

// 安装 bundle
bundleManager.installOrUpgrade("org.apache.felix.gogo.runtime", 
    new MavenCoords("org.apache.felix", "org.apache.felix.gogo.runtime", "1.1.6"));

// 卸载 bundle
bundleManager.uninstallBundle("org.apache.felix.gogo.command");

// 查询已安装 bundle
List<String> installed = bundleManager.getInstalledBundleNames();

// 获取 bundle 状态快照
List<Map<String, Object>> status = bundleManager.getBundleStatus();
```

### OSGi 服务桥接

```java
@Autowired
private ObjectContext objectContext;

// 通过 ObjectContext 查询 OSGi 服务
MyService service = objectContext.getBeanOfType(MyService.class);

// 查询所有同类型服务
List<MyService> services = objectContext.getBeansOfType(MyService.class);
```

### 获取 OSGi Launcher

```java
@Autowired
private FelixOsgiLauncher osgiLauncher;

// 检查框架状态
boolean active = osgiLauncher.isActive();

// 获取所有 bundle
List<OsgiBundle> bundles = osgiLauncher.getBundles();

// 安装 bundle
osgiLauncher.installBundle("mvn:org.apache.felix/org.apache.felix.gogo.runtime/1.1.6");

// 卸载 bundle
osgiLauncher.uninstallBundle("org.apache.felix.gogo.command");
```

## 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                   │
│  ┌─────────────────┐    ┌──────────────────────────────┐   │
│  │ OsgiAutoConfig  │    │  OsgiShellBundleManager      │   │
│  │                 │    │  - installGogo()             │   │
│  │ @Bean           │───▶│  - installSshd()             │   │
│  │ osgiLauncher    │    │  - uninstallBundle()         │   │
│  └────────┬────────┘    │  - upgradeBundle()           │   │
│           │             │  - getBundleStatus()         │   │
│           ▼             │  - scanExistingBundles()     │   │
│  ┌──────────────────────────────────────────────────┐  │
│  │         FelixOsgiLauncher (Felix Framework)       │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────┐   │  │
│  │  │ Gogo     │  │ Gogo     │  │ Apache SSHD  │   │  │
│  │  │ Runtime  │──▶│ Command  │──▶│ (sshd-osgi) │   │  │
│  │  │ (1.1.6)  │  │ (1.1.2)  │  │  (2.13.2)   │   │  │
│  │  └──────────┘  └──────────┘  └──────────────┘   │  │
│  │         ┌──────────┐                              │  │
│  │         │ Gogo     │◀── Telnet :6666             │  │
│  │         │ Shell    │◀── SSH    :8122             │  │
│  │         └──────────┘                              │  │
│  └──────────────────────────────────────────────────┘  │
│                          │                              │
│                          ▼                              │
│              OsgiBeanDefinitionRegister                 │
│              (桥接到 ObjectContext)                       │
└─────────────────────────────────────────────────────────────┘
```

## Bundle 安装顺序

OSGi bundle 必须按依赖顺序安装：

```
1. org.apache.felix.gogo.runtime   → 导出 org.apache.felix.service.command
2. org.apache.felix.gogo.command   → 依赖 gogo.runtime
3. org.apache.felix.gogo.shell     → 依赖 gogo.command
4. org.apache.sshd.sshd-osgi       → SSH 远程管理（可选）
5. org.apache.sshd.sshd-sftp       → SFTP 子系统（可选）
6. org.apache.sshd.sshd-scp        → SCP 传输（可选）
```

## 版本升级策略

`OsgiShellBundleManager` 自动执行版本比较：

```
已安装: org.apache.felix.gogo.runtime:1.1.4
仓库版本: 1.1.6
─────────────────────────────
操作: 卸载旧版 → 安装新版 1.1.6
```

版本比较规则（语义化版本）：
- `1.10.0` > `1.9.0` （主版本号相同时比较次版本号）
- `2.0.0` > `1.99.99` （主版本号优先）

## 安全建议

1. **生产环境修改默认密码**
```yaml
plugin:
  osgi:
    shell-bundles:
      sshd-username: your-username
      sshd-password: your-strong-password
```

2. **限制访问 IP**
```yaml
plugin:
  osgi:
    shell:
      host: 192.168.1.100  # 仅内网访问
```

3. **禁用 Telnet（仅使用 SSH）**
```yaml
plugin:
  osgi:
    shell:
      enabled: false  # 禁用 Gogo Telnet
    shell-bundles:
      install-gogo: false  # 不安装 Gogo
      install-sshd: true   # 仅安装 SSHD
```

## 与 spring-support-ssh-starter 的关系

| 特性 | spring-support-osgi-starter | spring-support-ssh-starter |
|------|----------------------------|---------------------------|
| 定位 | OSGi 框架内的远程管理 | 独立 SSH 服务端 |
| 协议 | SSH/Telnet (Gogo + SSHD) | SSH (Apache SSHD) |
| 集成方式 | 通过 OSGi bundle 安装 | 直接启动 SshServer |
| 管理界面 | OSGi Gogo Shell | Spring Boot Actuator |
| 推荐场景 | OSGi 插件化架构 | 独立 SSH 服务 |

两者可共存：OSGi 内通过 SSHD bundle 提供 OSGi 管理，Spring Boot 层面通过 ssh-starter 提供应用级 SSH 服务。

## 测试

运行集成测试：

```bash
cd spring-support-parent-starter/spring-support-osgi-starter
mvn test -Dtest=OsgiRealIntegrationTest
mvn test -Dtest=OsgiSpringSimulator
```

测试覆盖：
- Felix 框架启动/停止
- Gogo bundle 安装
- OSGi 服务注册与查询
- ObjectContext 桥接
- 属性默认值验证
- Bundle 状态快照
- 版本升级逻辑

## 版本历史

- **4.0.0.43** — 新增 SSHD bundle 管理、版本升级优先、完整文档
- **4.0.0.42** — 初始版本，Felix + Gogo + ObjectContext 桥接
