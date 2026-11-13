# spring-support-bundle-starter

OSGi Bundle 生命周期管理 Starter：以 Maven 坐标（`groupId:artifactId:version`）为入口，
提供 Bundle 的安装、原地升级、卸载与状态查询，并把「启动时批量装卸」收敛成一组配置项。

## 一、模块能力

| 能力域 | 关键类 | 入口（REST 路径或 SPI） |
|---|---|---|
| 自动配置装配 | `com.chua.starter.bundle.configuration.BundleAutoConfiguration` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| 坐标安装 / 原地升级 | `com.chua.starter.bundle.service.BundleManagerService#install(String)` | 无 REST，Java 调用 |
| 批量安装 | `BundleManagerService#installAll(List<String>)` | 无 REST，Java 调用 |
| 卸载 / 批量卸载 | `BundleManagerService#uninstall(String)`、`#uninstallAll(List<String>)` | 无 REST，Java 调用 |
| 运行状态查询 | `BundleManagerService#getStatus()`、`#isInstalled(String)` | 无 REST，Java 调用 |
| 托管清单 | `BundleManagerService#getManagedBundles()` | 无 REST，Java 调用 |
| 启动期批量装卸 | `BundleAutoConfiguration#bundleStartupProcessor`（`SmartInitializingSingleton`） | `plugin.bundle.install-at-startup` / `plugin.bundle.uninstall-at-startup` |
| 安装结果模型 | `BundleManagerService.InstallResult`（`record`） | 由 `install` / `installAll` 返回 |
| 服务信息登记 | `BundleAutoConfiguration#bundleServiceInfoContributor` | `ServiceInfoContributor.registerFlag("bundle", "OSGi Bundle", true, "OSGi Bundle 生命周期管理")` |

本模块**没有 Controller**，不暴露任何 REST 路径；能力全部以 Spring Bean 的形式提供，
由宿主代码注入 `BundleManagerService` 后调用。

自动装配成立需要同时满足两个条件（见 `BundleAutoConfiguration` 类注解）：

- 类路径上存在 `com.chua.osgi.support.FelixOsgiLauncher` 与 `com.chua.common.support.osgi.OsgiLauncher`
- `plugin.bundle.enabled=true`

## 二、依赖与引入

Maven 坐标（版本取父工程 `spring-support-parent-starter` 的真实值）：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-bundle-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 版本来源 | 作用 |
|---|---|---|
| `spring-support-common-starter` | 父工程 `dependencyManagement`（`${project.version}`） | `ServiceInfoContributor` 服务信息登记 |
| `utils-support-osgi-starter` | `${utils.version}` = `4.0.0.50` | `FelixOsgiLauncher` 实现、`OsgiLauncher` 抽象层 |
| `spring-boot-autoconfigure` | Spring Boot BOM（父级 `4.1.0`） | `@AutoConfiguration` 等自动装配设施 |
| `spring-boot-configuration-processor` | Spring Boot BOM，`optional` | 生成配置元数据 |
| `lombok` | 父工程 `1.18.48`，`optional` | `@Slf4j` / `@Data` |

注意：`utils-support-osgi-starter` 只带来了 `FelixOsgiLauncher` 类，
真正把 `OsgiLauncher` 注册成 Bean 的是 `spring-support-osgi-starter`。
宿主若只引本模块而不引 `spring-support-osgi-starter`，容器里没有 `OsgiLauncher` 可注入，
自动配置不成立（`BundleAutoConfiguration` 的 `bundleManagerService(OsgiLauncher, BundleProperties)` 无法装配）。

## 三、配置项

前缀：`plugin.bundle`（`com.chua.starter.bundle.properties.BundleProperties`，`@ConfigurationProperties(prefix = "plugin.bundle")`）。

| key | 默认值 | 说明 |
|---|---|---|
| `plugin.bundle.enabled` | `false` | 是否启用 Bundle 管理器；默认关闭，必须显式置 `true` |
| `plugin.bundle.install-at-startup` | 空列表 | 启动时自动安装的 Maven 坐标列表，格式 `groupId:artifactId:version` |
| `plugin.bundle.uninstall-at-startup` | 空列表 | 启动时自动卸载的 Bundle 符号名列表 |
| `plugin.bundle.auto-upgrade` | `true` | 已装同符号名 Bundle 时，是否允许升级到更高版本 |
| `plugin.bundle.repository-url` | `https://repo1.maven.org/maven2` | 远程解析 Bundle 的 Maven 仓库基址 |

`repository-url` 的用法是「基址 + Maven 标准路径」：
`{groupId 点换斜杠}/{artifactId}/{version}/{artifactId}-{version}.jar`（见 `BundleManagerService#toMavenUrl`）。
基址末尾的 `/` 会被去掉一次，因此写不写结尾斜杠都可以。

示例：

```yaml
plugin:
  bundle:
    enabled: true
    auto-upgrade: true
    repository-url: https://repo1.maven.org/maven2
    uninstall-at-startup:
      - org.apache.felix.gogo.runtime
    install-at-startup:
      - org.apache.felix:org.apache.felix.gogo.runtime:1.1.6
```

## 四、快速开始

**第一步：引入依赖。** 同时引入 `spring-support-osgi-starter`（提供 `OsgiLauncher` Bean，
并决定 Felix 框架的缓存目录、启动级别等）与本模块。

**第二步：打开开关。** 在 `application.yml` 中设置 `plugin.bundle.enabled: true`。
默认值是 `false`，不打开时本模块不注册任何 Bean。

**第三步：注入并调用。** 注入 `BundleManagerService`，按坐标安装：

```java
@Service
@RequiredArgsConstructor
public class BundleBootstrap {

    private final BundleManagerService bundleManager;

    public void install() {
        BundleManagerService.InstallResult result =
                bundleManager.install("org.apache.felix:org.apache.felix.gogo.runtime:1.1.6");
        if (!result.success()) {
            throw new IllegalStateException("安装失败: " + result.message());
        }
    }
}
```

## 五、运行示例

### 5.1 调用示例

**本模块无链式 API。** `install` / `uninstall` 返回结果对象或布尔值，不返回 `this`，
`BundleManagerService` 也没有 Builder，调用方式只能是「注入 → 逐个方法调用」。
`InstallResult` 是 `record`，取值用 `artifactId()` / `version()` / `success()` / `message()`。

单坐标安装（含自动升级判定）：

```java
@Autowired
private BundleManagerService bundleManager;

// 已装同 artifactId 的更高版本时直接跳过；请求版本更高时先走框架原生 update，
// 失败再回退为「卸载旧版 + 安装新版」。
BundleManagerService.InstallResult one =
        bundleManager.install("org.apache.felix:org.apache.felix.gogo.runtime:1.1.6");
log.info("success={}, artifactId={}, version={}, message={}",
        one.success(), one.artifactId(), one.version(), one.message());
```

批量安装（逐个安装，返回顺序与入参一致）：

```java
List<BundleManagerService.InstallResult> results = bundleManager.installAll(List.of(
        "org.apache.felix:org.apache.felix.gogo.runtime:1.1.6",
        "org.apache.felix:org.apache.felix.gogo.shell:1.1.4"));
for (BundleManagerService.InstallResult r : results) {
    if (!r.success()) {
        log.warn("[Bundle] {} 安装失败: {}", r.artifactId(), r.message());
    }
}
```

卸载与批量卸载：

```java
boolean removed = bundleManager.uninstall("org.apache.felix.gogo.runtime");
List<Boolean> flags = bundleManager.uninstallAll(List.of("org.apache.felix.gogo.shell"));
```

状态查询（`getStatus()` 每项是一个 `LinkedHashMap`，键为
`bundleId`、`symbolicName`、`state`、`version`、`location`、`managed`）：

```java
for (Map<String, Object> row : bundleManager.getStatus()) {
    log.info("{} [{}] {} managed={}", row.get("symbolicName"), row.get("state"),
            row.get("version"), row.get("managed"));
}

boolean installed = bundleManager.isInstalled("org.apache.felix.gogo.runtime");
List<String> managed = bundleManager.getManagedBundles();   // 返回 artifactId
```

`getStatus()` 在 OSGi 框架未激活（`launcher.isActive() == false`）时返回空列表，
而不是抛异常。

### 5.2 SPI 扩展示例

**本模块不提供 SPI 扩展点。** `src/main/resources/META-INF` 下只有
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
（内容为 `com.chua.starter.bundle.configuration.BundleAutoConfiguration`），
没有 `META-INF/services/*`，也没有自定义扩展点接口。

需要替换/扩展时，走 Spring 自身的扩展方式：

- 自己声明一个 `OsgiLauncher` Bean（或复用 `spring-support-osgi-starter` 提供的），
  `BundleAutoConfiguration` 通过构造参数注入它；
- 需要不同的安装策略时，不修改本模块，直接在宿主里包一层自己的服务调用
  `launcher.installBundle(url)` / `launcher.updateBundle(symbolicName, url)` /
  `launcher.uninstallBundle(symbolicName)`（这三个方法即 `BundleManagerService` 内部使用的框架能力）。

## 六、与其它 starter 的关系

依赖方向（本模块 → 依赖）：

- `spring-support-common-starter` — `ServiceInfoContributor`，用于把 `bundle` 开关登记到服务信息
- `utils-support-osgi-starter`（`4.0.0.50`）— `FelixOsgiLauncher`；`OsgiLauncher` 抽象层位于
  `com.chua.common.support.osgi`（由该依赖传递引入）

运行期还需要（不在本模块 pom 中声明）：

- `spring-support-osgi-starter` — 注册 `OsgiLauncher` Bean，并提供 `/osgi/*` 观测端点与 Gogo/SSHD 管理通道

反向依赖：父工程 `pom.xml` 的 `<dependencyManagement>` 收录了本模块（版本 `${project.version}`），
但仓库内没有任何 starter 把本模块声明为依赖；`spring-support-osgi-starter` 的 README
以「可选能力」的方式提到本模块，二者是**并列引入**关系，不是依赖关系。

与 `spring-support-osgi-starter` 的分工：

- `spring-support-osgi-starter`：Felix 框架启动、目录热部署、SSH/Telnet 管理、只读观测端点
- 本模块：只做「Maven 坐标 → 远程 jar → 安装/升级/卸载」这一层，并提供启动期批量装卸的配置项

## 七、已知限制

1. **只有 Java API，没有 REST 入口。** 全部能力需要宿主代码注入 `BundleManagerService` 调用，
   不存在 `/bundle/**` 之类的控制器。
2. **按坐标直接取 jar，不解析 POM。** `install(String)` 只把坐标拼成
   `.../{artifactId}-{version}.jar` 后交给 `launcher.installBundle(url)`，
   不会读取 `pom.xml`、不会预装传递依赖；bundle 的 `Import-Package` 仍需框架侧已满足。
3. **仓库地址不支持认证。** `repository-url` 只参与字符串拼接（`toMavenUrl`），
   没有账号/令牌/私服凭据的配置项。
4. **版本比较只认前三段数字。** `VERSION_PATTERN` 为 `(\d+)\.(\d+)\.(\d+)`，
   取不到三段数字时 `compareVersions` 返回 `0`，此时会被判定为「已是最新」而跳过安装。
5. **安装与卸载的键口径不同。** 安装按 `artifactId` 查找已装 Bundle（`findExisting`：
   先符号名精确等于 artifactId；再按来源文件名包含 `-{artifactId}-` / `-{artifactId}.`
   或以 `{artifactId}-数字` 开头匹配；最后退化为符号名子串包含）；
   卸载与 `isInstalled` 按符号名。`getManagedBundles()` 对外返回 artifactId，
   内部 `managedBundles` 以符号名为键。
6. **`InstallResult.artifactId` 在成功路径上装的是符号名。**
   `install` 内调用的是 `InstallResult.success(existing.getSymbolicName(), ...)` 与
   `success(installed.getSymbolicName(), ...)`，而失败路径 `fail(coords, message)` 放的是入参坐标；
   字段名与内容不完全对应，使用方以 `success()` + `message()` 判定更稳妥。
7. **启动期装卸只执行一次。** `bundleStartupProcessor` 是 `SmartInitializingSingleton`，
   容器刷新后跑一遍；单个坐标失败只记 `warn`（`installAll` 内部逐个捕获，
   `uninstall` 异常也被吞掉），不影响应用启动。
8. **卸载等待有上限。** 回退重装路径上 `awaitUninstalled` 最多等 5000ms（每 50ms 轮询一次），
   超时后仍会尝试安装新版；框架彻底卸载较慢时可能拿到「安装失败」。
