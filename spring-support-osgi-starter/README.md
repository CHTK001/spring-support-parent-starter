# Spring Support OSGi Starter

基于 Apache Felix 的 OSGi 框架集成模块，为 Spring Boot 应用提供完整的 OSGi 运行时支持。

## 模块架构

```
spring-support-osgi-starter      ← Felix 启动 + Gogo/SSHD 远程管理 + 目录热部署 + 只读观测端点
spring-support-bundle-starter    ← Maven 坐标驱动的 Bundle 生命周期管理（含原生热升级）
utils-support-osgi-starter       ← Felix 实现层（FelixOsgiLauncher / BundleDeployWatcher / Bean 桥接）
utils-support-common-starter     ← 纯 JDK 抽象层 com.chua.common.support.osgi
```

## 功能特性

### 核心能力
- **Felix OSGi 框架** — Apache Felix 7.0.5，bundle 全生命周期管理
- **框架驱动的事件模型** — 生命周期事件由框架级 `BundleListener` 投递，客户端方法不再手工触发
- **服务引用持有** — `getService` 取到的实例在显式释放前不会被框架回收
- **原生热升级** — `updateBundle` 走框架 `Bundle#update`，保留 bundle 标识与依赖解析结果
- **目录热部署** — 周期扫描部署目录，自动安装 / 原地升级 / 卸载，实现无重启迭代
- **Gogo Shell** — runtime/command/shell 三件套自动安装
- **Apache SSHD** — sshd-core/osgi/sftp/scp，SSH 远程管理（默认）
- **协议选择** — SSH 优先（端口 8122），Telnet 回退（端口 6666）
- **服务桥接** — OSGi 服务经 `OsgiBeanDefinitionRegister` 桥接进 `ObjectContext`
- **只读观测端点** — `/osgi/stats`、`/osgi/bundles`、`/osgi/services`、`/osgi/context`

---

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

### 2. 完整配置

```yaml
plugin:
  osgi:
    enabled: true
    storage: ./osgi-cache
    storage-clean: onFirstInit      # onFirstInit | onInit | none（none 支持重启续用）
    start-level: 4
    auto-start-installed-bundles: true

    # 原样透传给 Felix 框架，用于导出跨 bundle 共享的接口包
    framework-properties:
      org.osgi.framework.system.packages.extra: com.chua.common.support.osgi

    # 目录热部署（开发/测试环境适用）
    deploy:
      enabled: true
      directory: ./osgi-deploy
      polling-interval: 10000
      uninstall-on-removed: true    # jar 被删除则卸载
      update-on-changed: true       # jar 内容变化则原地升级

    # 远程管理（SSH 与 Telnet 二选一）
    shell:
      enabled: true
      protocol: ssh
      host: 127.0.0.1
      port: 8122
      username: admin
      password: your-password
      host-key: target/osgi-hostkey.ser
      allow-empty-credential: false # 默认禁止空用户名+空密码登录
      max-auth-attempts: 3

    # 只读观测端点（默认关闭，需纳入既有鉴权体系）
    endpoint:
      enabled: true

  bundle:
    enabled: true
    auto-upgrade: true
    repository-url: https://repo1.maven.org/maven2
    install-at-startup:
      - org.apache.felix:org.apache.felix.gogo.runtime:1.1.6
    uninstall-at-startup:
      - org.example.legacy-bundle
```

### 3. 连接

```bash
ssh admin@localhost -p 8122     # SSH（推荐）
telnet localhost 6666           # Telnet（回退）
```

SSH 会话内可用命令：

| 命令 | 说明 |
|---|---|
| `bundle:list` | 列出全部 bundle（编号 / 符号名 / 版本 / 状态） |
| `bundle:install <位置>` | 安装 bundle（文件路径或 URL） |
| `bundle:update <符号名>` | 原地升级（沿用原位置） |
| `bundle:start <符号名>` | 启动 bundle |
| `bundle:stop <符号名>` | 停止 bundle |
| `bundle:uninstall <符号名>` | 卸载 bundle |

其余命令转发给框架内的 Gogo 命令处理器。

> **认证要求**：`username` 与 `password` 均不得为空，否则启动时直接拒绝创建 SSH 服务端。
> 如确需空凭据访问（仅限完全受信任的本地调试），须显式设置 `allow-empty-credential: true`。

### 4. API 使用

```java
@Autowired private OsgiLauncher launcher;
@Autowired private BundleManagerService bundleManager;

// 按 Maven 坐标安装（已装更高版本时自动走原地升级）
bundleManager.install("org.example:my-bundle:1.0.0");
bundleManager.uninstall("org.example.my-bundle");
List<Map<String, Object>> status = bundleManager.getStatus();

// 框架 API
OsgiBundle bundle = launcher.installBundle("file:/path/to/my-bundle.jar");
launcher.updateBundle("org.example.my-bundle", "file:/path/to/my-bundle-1.1.0.jar");
launcher.uninstallBundle("org.example.my-bundle");
launcher.getBundle("org.example.my-bundle").getHeaders();

// 服务查询（取到的实例由启动器持有引用，release 前始终有效）
MyService svc = launcher.getService(MyService.class);
List<OsgiServiceDescriptor> all = launcher.getServiceDescriptors();
launcher.releaseServices(MyService.class);
```

### 5. 生命周期监听

```java
launcher.addListener(new BundleLifecycleListener() {
    @Override public void onBundleInstalled(String symbolicName) { }
    @Override public void onBundleStarted(String symbolicName) { }
    @Override public void onBundleStopped(String symbolicName) { }
    @Override public void onBundleUpdated(String symbolicName, String newVersion) { }
    @Override public void onBundleUninstalled(String symbolicName) { }
    @Override public void onBundleStateChanged(String symbolicName, String oldState, String newState) { }
    // 可选：解析失败回调（依赖无法满足）
    @Override public void onBundleResolveFailed(String symbolicName, String reason) { }
});
```

---

## 观测端点

启用 `plugin.osgi.endpoint.enabled=true` 后提供以下**只读**接口：

| 路径 | 返回 |
|---|---|
| `GET /osgi/stats` | 框架统计：状态、bundle 总数/活动数、各状态计数、持有引用数、服务类型清单 |
| `GET /osgi/bundles` | 全部 bundle 清单（编号、符号名、版本、状态、来源位置） |
| `GET /osgi/bundles/{symbolicName}` | 单 bundle 详情（追加 MANIFEST 头与已注册服务类型） |
| `GET /osgi/services` | 服务注册表全部登记（类型、pid、实现类、服务属性） |
| `GET /osgi/context` | 框架上下文可用性 |

变更类操作（安装/卸载/升级）**不在 HTTP 端点暴露**，需经受独立凭据保护的远程管理通道执行。

---

## 版本历史

- **4.0.0.43** — 生命周期事件改由框架 `BundleListener` 驱动；新增原生热升级 `updateBundle`；服务引用由启动器持有；新增目录热部署 `BundleDeployWatcher`；新增只读观测端点；修复 SSH 空凭据认证绕过、BundleManager 托管键不一致、unregisterService 误注销
- **4.0.0.42** — 初始版本
