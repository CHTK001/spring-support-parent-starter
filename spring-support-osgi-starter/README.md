# Spring Support OSGi Starter

基于 Apache Felix 的 OSGi 框架集成模块，为 Spring Boot 应用提供完整的 OSGi 运行时支持，包括 Gogo shell（Telnet）和 Apache SSHD（SSH）远程管理。

## 模块架构

```
spring-support-osgi-starter      ← Felix 框架 + Gogo/SSHD 远程管理 + ObjectContext 桥接
spring-support-bundle-starter    ← 独立 Bundle 生命周期管理（Maven 坐标安装/卸载/查询）
spring-support-ssh-starter       ← 独立 SSH 服务端（非 OSGi，纯应用层）
utils-support-osgi-starter       ← 核心 OSGi 工具（FelixOsgiLauncher、OsgiBeanDefinitionRegister）
```

## 功能特性

### 核心能力
- **Felix OSGi 框架** — Apache Felix 7.0.5，bundle 全生命周期管理
- **Gogo Shell** — runtime/command/shell 三件套，自动安装
- **Apache SSHD** — sshd-osgi + sshd-sftp + sshd-scp，SSH 远程管理（优先）
- **协议选择** — SSH 优先（端口 8122），Telnet 回退（端口 6666）
- **服务桥接** — OSGi 服务通过 ObjectContext 跨容器查询

### spring-support-bundle-starter
- **Maven 坐标驱动**：`groupId:artifactId:version` 格式安装 bundle
- **高版本优先**：自动比较版本号，升级时先卸载旧版
- **批量管理**：支持 install/uninstall/status 全操作
- **配置化**：启动时自动安装/卸载指定的 bundle 列表

## 快速开始

### 1. 依赖

```xml
<!-- OSGi 框架 + 远程管理 -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-osgi-starter</artifactId>
</dependency>
<!-- Bundle 生命周期管理（可选） -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-bundle-starter</artifactId>
</dependency>
```

### 2. 配置（SSH 远程管理）

```yaml
plugin:
  osgi:
    enabled: true
    storage: ./osgi-cache
    start-level: 4
    shell:
      enabled: true
      protocol: ssh
      port: 8122
      host: 0.0.0.0
      username: admin
      password: your-password
  bundle:
    enabled: true
    auto-upgrade: true
    repository-url: https://repo1.maven.org/maven2
    install-at-startup:
      - org.apache.felix:org.apache.felix.gogo.runtime:1.1.6
      - org.apache.felix:org.apache.felix.gogo.command:1.1.2
      - org.apache.felix:org.apache.felix.gogo.shell:1.1.4
    uninstall-at-startup:
      - org.example.legacy-bundle
```

### 3. 连接

```bash
# SSH（推荐）
ssh admin@localhost -p 8122

# Telnet（回退）
telnet localhost 6666
```

### 4. API 使用

```java
@Autowired
private FelixOsgiLauncher osgiLauncher;

@Autowired
private BundleManagerService bundleManager;

// 安装 bundle
bundleManager.install("org.example:my-bundle:1.0.0");

// 卸载 bundle
bundleManager.uninstall("org.example:my-bundle");

// 查询状态
List<Map<String, Object>> status = bundleManager.getStatus();

// OSGi 服务查询
MyService svc = osgiLauncher.getService(MyService.class);
```

## 测试覆盖

| 测试类 | 用例数 | 说明 |
|--------|--------|------|
| `OsgiRealIntegrationTest` | 8 | 核心 OSGi 功能（Felix 启动/停止/Gogo 安装/ObjectContext 桥接/属性验证） |
| `OsgiIntegrationTest` | 11 | 综合测试（Bundle 安装/SSHD/Telnet 配置/Bundle 状态/卸载） |
| `BundleManagerTest` | 7 | BundleManagerService 完整功能（安装/重复安装/状态/卸载/属性） |
| `OsgiSpringSimulator` | 7 | 模拟 Spring Boot 启动全流程 |

所有测试均为真实执行（无 JUnit，通过 `main` 方法 + `[PASS]`/`[FAIL]` 输出），在无 Gogo shell console 环境下禁用 shell 自动启动以避免阻塞。

## 版本历史

- **4.0.0.43** — 新增 spring-support-bundle-starter，Gogo+SSHD 内聚到 utils-support-osgi-starter，单一协议设计
- **4.0.0.42** — 初始版本
