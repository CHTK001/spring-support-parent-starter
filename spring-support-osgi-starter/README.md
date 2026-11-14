# spring-support-osgi-starter

集成 Apache Felix OSGi 框架的 Spring Boot starter：随应用启动 Felix 框架，提供 Gogo shell 远程管理（SSH/Telnet）、目录热部署、源码扩展点热重载、OSGi 服务到 ObjectContext 的 Bean 桥接与只读观测端点。
框架本体默认开启（`plugin.osgi.enabled` 缺省即 true），但远程管理（`plugin.osgi.shell.enabled`）、观测端点（`plugin.osgi.endpoint.enabled`）、目录热部署（`plugin.osgi.deploy.enabled`）默认均关闭，需显式开启。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| 自动配置 | `OsgiAutoConfiguration` | `plugin.osgi.enabled`（默认 true） |
| 配置绑定 | `OsgiProperties` | `plugin.osgi.*` |
| Felix 框架启动 | `FelixOsgiLauncher`（utils-support-osgi-starter） | `osgiLauncher` Bean |
| Gogo bundle 安装 | `OsgiAutoConfiguration#installShellBundles` | `plugin.osgi.shell.enabled=true` |
| SSH 远程管理（应用层） | `GogoSshShellServer` | `shell.enabled=true` + `shell.protocol=ssh` |
| 目录热部署 | `BundleDeployWatcher`（utils-support-osgi-starter） | `plugin.osgi.deploy.enabled=true` |
| 源码扩展点热重载 | `SourceExtensionScheduler` / `SourceJavaCompiler` / `SourceGroovyLoader` | `plugin.osgi.source.enabled`（默认 true） |
| Bean 桥接（服务投影） | `OsgiBeanDefinitionRegister`（utils-support-osgi-starter） | 注册到 `ObjectContext` |
| 运行时观测端点 | `OsgiMonitorController` | `plugin.osgi.endpoint.enabled=true` |
| 服务信息登记 | `osgiServiceInfoContributor` | 控制台模块能力 |

实际生效的能力：

- 启动并停止 Felix 框架，持久化存储目录 `plugin.osgi.storage`（默认 `./osgi-cache`）；
- shell 开启时自动安装 Gogo runtime / command / shell bundle；
- SSH 远程管理由应用层 `GogoSshShellServer`（MINA SSHD 2.x）直接提供，命令会话接线到框架内 Gogo `CommandProcessor`，支持 `bundle:list / install / update / start / stop / uninstall`；
- Telnet 远程管理经 Felix 框架配置 `osgi.shell.telnet.*` 交由 Gogo shell bundle 提供；
- 目录热部署：扫描部署目录自动安装新 bundle、卸载已移除 bundle、可原地升级；
- 源码扩展点：目录下的 Java（可选 Groovy）源码被持续编译并注册进 `ServiceProvider` 扩展点表，无需打成 bundle jar；
- OSGi 服务经 `OsgiBeanDefinitionRegister` 桥接为 Spring `ObjectContext` 的扩展对象，bundle 生命周期变化会推进快照并触发扩展对象切换；
- 观测端点只读接口：`/osgi/stats`、`/osgi/bundles`、`/osgi/bundles/{symbolicName}`、`/osgi/services`、`/osgi/snapshot`、`/osgi/context`；
- 变更类操作（上传安装/升级/启动/停止/卸载/下载）受 `plugin.osgi.endpoint.manage-enabled`（默认 true）控制，端点启用后由既有接口鉴权体系保护。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-osgi-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 |
|:-----|:-----|
| `spring-support-common-starter` | `ModuleLog`、`ServiceInfoContributor`、公共 `ObjectContext` |
| `utils-support-osgi-starter` | `FelixOsgiLauncher`、`BundleDeployWatcher`、`OsgiBeanDefinitionRegister`、`SourceExtensionScheduler` 等 OSGi 实现 |
| `spring-boot-autoconfigure` | 自动配置机制（`@AutoConfiguration` / `@ConditionalOnProperty`） |
| `com.jcraft:jsch` | 仅 test 作用域使用 |

Felix 框架、Gogo bundle、SSHD 的版本均由 `utils-support-osgi-starter` 及父 pom 的依赖管理决定，本模块未单独钉版本。

## 三、配置项

配置前缀：**`plugin.osgi`**。

### 3.1 框架

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.osgi.enabled` | `true` | 是否装配 OSGi 集成（缺省即开启） |
| `plugin.osgi.storage` | `./osgi-cache` | Felix 框架持久化存储目录 |
| `plugin.osgi.start-level` | `4` | Felix 框架初始启动级别 |
| `plugin.osgi.storage-clean` | `onFirstInit` | 存储清理策略：`onFirstInit`（首次初始化清空）/ `onInit`（每次初始化清空）/ `none`（保留上次 bundle 状态） |
| `plugin.osgi.auto-start-installed-bundles` | `true` | 安装 bundle 后是否自动启动 |
| `plugin.osgi.framework-properties` | 空 Map | 自定义 Felix 框架配置，原样透传（如 `org.osgi.framework.system.packages.extra`） |

> 框架会固定写入 `gosh.args=--noshutdown`，防止 Gogo shell 在 stdin 到 EOF（systemd/docker 非交互启动）时把整个框架停掉；该默认值先于用户自定义属性写入，仍可被 `framework-properties` 覆盖。

### 3.2 目录热部署（deploy）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.osgi.deploy.enabled` | `false` | 是否启用目录热部署；**默认关闭** |
| `plugin.osgi.deploy.directory` | `./osgi-deploy` | 部署目录，放入 jar 即安装启动 |
| `plugin.osgi.deploy.polling-interval` | `10000` | 扫描间隔（毫秒） |
| `plugin.osgi.deploy.uninstall-on-removed` | `true` | 目录中 jar 移除后自动卸载对应 bundle |
| `plugin.osgi.deploy.update-on-changed` | `false` | 移除后是否对已安装 bundle 做原地升级（保留 bundle 标识与状态） |

### 3.3 源码扩展点（source）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.osgi.source.enabled` | `true` | 源码扩展点热重载总开关（缺省即开启） |
| `plugin.osgi.source.directory` | `./osgi-source` | 源码目录；不存在时按「无源码」处理，不报错 |
| `plugin.osgi.source.output-directory` | 空串 | 编译产物输出目录；留空取 `<directory>/../osgi-source-classes`，fat jar 部署时会抽取 `BOOT-INF/lib` 依赖到该目录（实测约 685MB，应用停止时清理） |
| `plugin.osgi.source.polling-interval` | `5000` | 扫描间隔（毫秒），指纹未变化不触发编译 |
| `plugin.osgi.source.groovy-enabled` | `true` | 是否启用 Groovy 脚本；classpath 无对应引擎时告警降级为仅 Java |
| `plugin.osgi.source.script-engine` | `groovy` | 脚本引擎名，对应 `ScriptProvider#engineName` |

### 3.4 Bean 桥接（bridge）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.osgi.bridge.acceptance-enabled` | `true` | 桥接定义是否经过 `BeanAcceptance` 验收器链，拒绝的投影不纳入快照 |
| `plugin.osgi.bridge.invalidate-on-bundle-lifecycle` | `true` | bundle 生命周期变化时失效桥接快照；关闭后退化为每次查询都重新枚举框架 |

### 3.5 观测端点（endpoint）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.osgi.endpoint.enabled` | `false` | 是否暴露 `/osgi/**` 端点；**默认关闭**，开启会暴露插件符号名、版本、来源位置 |
| `plugin.osgi.endpoint.manage-enabled` | `true` | 是否允许变更类操作（上传安装、升级、启动/停止、卸载、下载）；只在 `enabled=true` 时才有意义 |

### 3.6 远程管理（shell）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.osgi.shell.enabled` | `false` | 是否启用远程管理；**默认关闭** |
| `plugin.osgi.shell.protocol` | `ssh` | 协议：`ssh`（默认）或 `telnet`，同一实例只启用一种 |
| `plugin.osgi.shell.port` | `0` | 监听端口；SSH 模式 0 时取 8122，Telnet 模式 0 时取 6666 |
| `plugin.osgi.shell.host` | `127.0.0.1` | 绑定地址 |
| `plugin.osgi.shell.username` | 空串 | SSH 认证用户名；默认空，配合 `allow-empty-credential=false` 会拒绝一切认证 |
| `plugin.osgi.shell.password` | 空串 | SSH 认证密码 |
| `plugin.osgi.shell.host-key` | 空串 | 主机密钥文件路径；留空使用 `target/osgi-hostkey.ser`，首次启动自动生成 |
| `plugin.osgi.shell.allow-empty-credential` | `false` | 允许空凭据登录；开启后空用户名+空密码即可登录，等同无认证暴露 |
| `plugin.osgi.shell.max-auth-attempts` | `3` | 认证失败次数上限；**仅声明，运行期未读取**（`GogoSshShellServer` 认证逻辑未消费该字段） |
| `plugin.osgi.shell.install-framework-sshd-bundles` | `false` | 是否把 Apache SSHD 自身也作为 OSGi bundle 安装进框架；**默认关闭**（见「七、已知限制」） |

## 四、快速开始

**第一步：引入依赖**（见上）。

**第二步：配置 yaml**（开启 SSH 远程管理 + 观测端点）：

```yaml
plugin:
  osgi:
    # 框架本体默认开启，无需显式写 enabled: true
    shell:
      enabled: true
      protocol: ssh
      host: 127.0.0.1
      port: 8122
      username: admin
      password: change-me          # username/password 必须都非空，否则启动抛异常
    endpoint:
      enabled: true                 # 开启 /osgi/** 只读观测端点
```

**第三步：验证**

1. 启动日志出现 `[OSGi] Felix 框架已启动，OSGi 集成就绪`、`[OSGi] SSH shell 已监听: 127.0.0.1:8122`；
2. SSH 登录后执行 `bundle:list` 查看框架内 bundle：
   ```bash
   ssh -p 8122 admin@127.0.0.1
   g! bundle:list
   ```
3. 浏览器或 curl 查询观测端点：
   ```bash
   curl http://127.0.0.1:8080/osgi/stats
   curl http://127.0.0.1:8080/osgi/bundles
   ```

（`/osgi/**` 端点走应用自身的端口与鉴权体系；不开启 `shell.enabled` 时不会监听 8122。）

## 五、运行示例

### 5.1 调用示例

注入框架与桥接注册器查询运行状态：

```java
@Service
public class OsgiStatusDemo {

    private final FelixOsgiLauncher launcher;
    private final ObjectProvider<OsgiBeanDefinitionRegister> register;

    public OsgiStatusDemo(FelixOsgiLauncher launcher,
                          ObjectProvider<OsgiBeanDefinitionRegister> register) {
        this.launcher = launcher;
        this.register = register;
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", launcher.isActive());
        result.put("bundleCount", launcher.getBundles().size());
        OsgiBeanDefinitionRegister r = register.getIfAvailable();
        result.put("bridgeAvailable", r != null);
        if (r != null) {
            result.put("definitionCount", r.getBeanDefinitionNames().size());
            result.put("rejected", r.getRejectedSnapshots());
        }
        return result;
    }
}
```

SSH 远程管理命令（`bundle:` 前缀由 `GogoSshShellServer` 直接处理）：

```text
bundle:list
bundle:install file:/path/to/plugin.jar
bundle:start <符号名>          # 也支持数字 bundleId
bundle:stop <符号名>
bundle:update <符号名>
bundle:uninstall <符号名>
```

### 5.2 SPI 扩展示例

本模块的扩展点来自源码热重载：把带 `@SourceSpi` 的 Java 类放进 `plugin.osgi.source.directory`，改动即编译并注册进 `ServiceProvider` 扩展点表，**不需要打成 bundle jar**：

```java
@SourceSpi
public class MyExtensions {
    public final MySpi myExt = new MySpiImpl();   // 扩展名 = 字段名 myExt
}
```

说明：

- 该扩展点是 utils-support-osgi-starter 的 `ServiceProvider` / `ScriptProvider` 机制，不是 Spring `META-INF/extensions`；
- 若要替换「Bean 桥接 / 验收」行为，可注入自定义 `OsgiBeanDefinitionRegister` 或自行实现 `BeanAcceptance` 链；
- Felix 自定义框架行为通过 `plugin.osgi.framework-properties` 透传，无需写代码。

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
|:-----|:-----|:-----|
| `spring-support-common-starter` | 本模块依赖 | `ModuleLog`、`ServiceInfoContributor`、公共 `ObjectContext` |
| `utils-support-osgi-starter` | 本模块依赖 | `FelixOsgiLauncher`、`BundleDeployWatcher`、`OsgiBeanDefinitionRegister`、源码编译/加载实现 |
| `spring-support-ssh-starter` | 独立模块，非本模块依赖 | 通用 SSH 服务器端（登录+Shell+SFTP）；本模块的 SSH 仅面向 OSGi Gogo 远程管理，两者用途不同 |
| `vue-support-parent-starter` | 不相关 | 本模块不托管任何前端页面 |

注意区分：本模块的「SSH 远程管理」是 Felix 框架内 Gogo 命令的通道（bundle 运维），不是通用 SSH 登录服务。

## 七、已知限制

- **远程管理默认关闭**：`shell.enabled` / `endpoint.enabled` / `deploy.enabled` 默认均为 false，只引入依赖不会开放端口。
- **`max-auth-attempts` 未接入**：`plugin.osgi.shell.max-auth-attempts` 只有字段与默认值，`GogoSshShellServer#authenticate` 未读取，认证失败次数限制交给 SSHD 默认行为。
- **空凭据是红线**：`username` / `password` 任一为空且 `allow-empty-credential=false` 时，`GogoSshShellServer` 启动直接抛异常；开启空凭据即无认证暴露，仅限受信任的本地调试环境。
- **框架内 sshd bundle 默认不装**：`install-framework-sshd-bundles=true` 在 sshd 2.13.2 下无法真正工作——`sshd-sftp` / `sshd-scp` 声明 Import `org.apache.sshd.client;version="[2.13.2,2.14)"`，而唯一导出该包的 `sshd-osgi` 导出不带 version（视作 0.0.0），版本区间无法满足，两个 bundle 必停在 `INSTALLED`。SSH 实际由应用层 `GogoSshShellServer` 提供，与框架无关。
- **框架启动失败不阻断应用**：`osgiLauncher` 启动失败只打 error 日志，但 `OsgiBeanDefinitionRegister` 等 Bean 仍会装配（挂到未启动的框架上）；gogo shell 若 `CommandProcessor` 服务不可用，`bundle:` 命令仍可用（框架 API 直通），其余 Gogo 命令不可用。
- **源码热重载即运行期加载代码**：`source.enabled` 默认开启，目录里放源码等同于授权运行期编译加载；fat jar 部署会抽取 `BOOT-INF/lib` 到 `output-directory`（disk 占用约一份 lib 的体量，实测 ~685MB），应用停止时清理。
- **单会话命令上限**：`GogoSshShellServer` 每个会话最多处理 10000 条命令，超出即断开（`MAX_COMMANDS_PER_SESSION`）。
- **上传校验**：只接受 `.jar`；必须含 `Bundle-SymbolicName`；文件名只取 basename 并校验落在部署目录内（防路径穿越）；同符号名已存在时拒绝重复安装，升级先备份原 jar、失败自动恢复。
- **端点暴露面**：`/osgi/**` 会暴露插件符号名、版本、来源位置；`manage-enabled` 默认 true 允许上传/卸载，生产环境必须纳入鉴权并只对管理员开放。