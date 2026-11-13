# spring-support-console-starter

管理控制台：用 FTL 页面在运行期临时调整 `@ApiFeature` / `@ApiGray` 覆盖与通用键值，
覆盖数据落 SQLite，并在启动时用「注解指纹」决定是否清空覆盖库。

## 一、模块能力

| 能力域 | 关键类 | 入口（REST 路径或 SPI） |
|---|---|---|
| 自动配置装配 | `com.chua.starter.console.ConsoleAutoConfiguration` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| 功能开关页面 | `com.chua.starter.console.controller.ConsolePageController#features` | `GET /console/features` |
| 灰度规则页面 | `ConsolePageController#grays` | `GET /console/grays` |
| 键值设置页面 | `ConsolePageController#kv` | `GET /console/kv` |
| 控制台首页 | `ConsolePageController#index` | `GET /console/`（302 到 `/console/features`） |
| 功能覆盖写入 / 删除 | `ConsolePageController#upsertFeature`、`#deleteFeature` | `POST /console/api/features`、`POST /console/api/features/delete` |
| 灰度覆盖写入 / 删除 | `ConsolePageController#upsertGray`、`#deleteGray` | `POST /console/api/grays`、`POST /console/api/grays/delete` |
| 键值写入 / 删除 | `ConsolePageController#upsertKv`、`#deleteKv` | `POST /console/api/kv`、`POST /console/api/kv/delete` |
| 注解扫描与指纹 | `com.chua.starter.console.scan.AnnotationSwitchScanner#scan()` → `SwitchInventory` | Spring Bean，`ConsolePageController` / `ConsoleStartupResetter` 调用 |
| SQLite 持久层 | `com.chua.starter.console.store.ConsoleSqliteStore` | Spring Bean（`destroyMethod = "close"`） |
| 覆盖判定接入 | `com.chua.starter.console.store.ConsoleOverrideProvider`（实现 `ApiSwitchOverrideProvider`） | Spring Bean，被 common-starter 的 `ApiControlInterceptor` 咨询 |
| 启动指纹比对 / 重置 | `com.chua.starter.console.store.ConsoleStartupResetter`（`ApplicationRunner`） | 应用启动完成后自动执行 |
| 键值注入环境 | `com.chua.starter.console.environment.ConsoleKvEnvironmentPostProcessor` | `META-INF/spring.factories`（`EnvironmentPostProcessor`） |
| 服务信息登记 | `ConsoleAutoConfiguration#consoleServiceInfoContributor` | `ServiceInfoContributor.registerFlag("console", ...)` |

页面路由与调整接口全部标注 `@Permission(ConsolePageController.CONSOLE_PERMISSION)`，
权限码常量为 `console:manage`，鉴权由 `spring-support-oauth-client-starter` 的
`PermissionInterceptor` 执行。

SQLite 中固定使用四张表（`ConsoleSqliteStore#initSchema`）：

| 表 | 用途 |
|---|---|
| `console_feature_override` | `@ApiFeature` 覆盖：`feature_id` 主键、`enabled`、`disabled_message`、`disabled_status` |
| `console_gray_override` | `@ApiGray` 覆盖：`pattern` 主键、`percentage`、`roles`、`users`、`headers`、`rule` |
| `console_kv` | 通用键值运行设置：`k` 主键、`v` |
| `console_meta` | 注解指纹：`k='annotation-fingerprint'` |

## 二、依赖与引入

Maven 坐标（版本取父工程真实值）：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-console-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 版本来源 | 作用 |
|---|---|---|
| `spring-support-oauth-client-starter` | 父工程 `dependencyManagement`（`${project.version}`） | `@Permission` 页面鉴权；传递引入 common-starter 的注解与拦截器体系 |
| `spring-boot-starter-web` | Spring Boot BOM | MVC 控制器与 `@RequestParam` 绑定 |
| `spring-boot-starter-freemarker` | Spring Boot BOM | 渲染 `templates/console/*.ftl` |
| `sqlite-jdbc`（`org.xerial`） | Spring Boot BOM | 覆盖库持久化，自带 `org.sqlite.JDBC` |
| `lombok` | 父工程 `1.18.48`，`provided` | `@Data` / `@Slf4j` |

模板文件在 jar 内的固定位置（类路径，不是文件系统）：

```text
templates/console/features.ftl
templates/console/grays.ftl
templates/console/kv.ftl
```

控制器返回值是视图名（`console/features` 等），依赖 FreeMarker 默认的
`classpath:/templates/` 前缀与 `.ftl` 后缀。宿主若改过
`spring.freemarker.prefix` / `suffix`，需要自行保证这三个模板可达。

## 三、配置项

前缀：`plugin.console`（`com.chua.starter.console.ConsoleProperties`，
`@ConfigurationProperties(prefix = ConsoleProperties.PRE)`，`PRE = "plugin.console"`）。

| key | 默认值 | 说明 |
|---|---|---|
| `plugin.console.enable` | `true` | 是否启用控制台；`matchIfMissing = true`，不写也生效 |
| `plugin.console.sqlite-path` | `console.sqlite` | SQLite 文件路径，按运行目录解析；环境注入器与持久层读同一个键 |
| `plugin.console.permission-code` | `console:manage` | 页面访问权限码（**当前代码未读取该值**，见「已知限制」） |
| `plugin.console.kv-prefix` | 空串 | 键值列表前缀过滤（**当前代码未读取该值**，见「已知限制」） |

示例：

```yaml
plugin:
  console:
    enable: true
    sqlite-path: ./data/console.sqlite
```

`plugin.console.enable` 与 `plugin.console.sqlite-path` 不只被 `ConsoleProperties` 使用：
`ConsoleKvEnvironmentPostProcessor` 在 Spring 环境准备阶段就用
`environment.getProperty("plugin.console.enable", "true")` 与
`environment.getProperty("plugin.console.sqlite-path", "console.sqlite")` 直接读取，
因此键名必须写成上述形式（不能用 `plugin.console.sqlitePath` 之类的驼峰变体）。

## 四、快速开始

**第一步：引入依赖。** 加上 `spring-support-console-starter`；
鉴权依赖 `spring-support-oauth-client-starter`（本模块已传递引入），
宿主需自行提供登录态与权限数据源，否则所有 `/console/**` 都会被 `PermissionInterceptor` 拦下。

**第二步：确认开关与库路径。** 默认 `plugin.console.enable=true` 即生效；
需要换持久化位置时设置 `plugin.console.sqlite-path`。目录必须可写，
否则 `ConsoleSqliteStore` 构造时抛 `IllegalStateException`（"SQLite 持久层初始化失败"）。

**第三步：访问页面。** 启动后打开 `/console/`，会 302 到 `/console/features`；
调整任意开关后立即写库，下一次请求即按覆盖值判定。

```text
GET  /console/features      → 功能开关列表（默认值 + 覆盖值 + 生效值）
GET  /console/grays         → 灰度规则列表
GET  /console/kv            → 键值运行设置列表
```

## 五、运行示例

### 5.1 调用示例

**本模块无链式 API。** `ConsoleSqliteStore` 的方法是普通 `synchronized` 方法，
返回 `void` / 布尔 / 记录对象，不返回 `this`；也没有 Builder。
`SwitchInventory` 及其内部 `FeatureDescriptor` / `GrayDescriptor` 带 Lombok `@Builder`，
但那是**扫描结果的构造**，不是服务调用链。

写入功能覆盖（等价于页面 `POST /console/api/features`）：

```java
@Autowired
private ConsoleSqliteStore store;

store.upsertFeatureOverride("order-export", false, "系统维护中", 503);
ConsoleSqliteStore.FeatureOverrideRow row = store.getFeatureOverride("order-export");
if (row != null) {
    log.info("enabled={}, message={}, status={}",
            row.enabled(), row.disabledMessage(), row.disabledStatus());
}
store.deleteFeatureOverride("order-export");   // 删除后回落到注解默认值
```

写入灰度覆盖（等价于页面 `POST /console/api/grays`）：

```java
store.upsertGrayOverride("gray-order-v2", 30,
        new String[]{"admin"}, new String[]{"u1001"},
        new String[]{"X-Gray: 1"}, "true");
ConsoleSqliteStore.GrayOverrideRow gray = store.getGrayOverride("gray-order-v2");
store.deleteGrayOverride("gray-order-v2");
```

键值运行设置（写入后由环境注入器在**下次启动**时注入为 `PropertySource`）：

```java
store.setKv("order.export.limit", "500");
String value = store.getKv("order.export.limit");
Map<String, String> all = store.listKv("");        // 前缀为空表示全部
store.deleteKv("order.export.limit");
```

业务侧读取键值（`ConsoleKvEnvironmentPostProcessor` 以 `MapPropertySource("console-kv")`
加到最前面，优先级最高）：

```java
@Value("${order.export.limit:100}")
private int exportLimit;
```

扫描与指纹（页面与启动重置器共用的逻辑）：

```java
@Autowired
private AnnotationSwitchScanner scanner;

SwitchInventory inventory = scanner.scan();
String fingerprint = inventory.getFingerprint();
for (SwitchInventory.FeatureDescriptor f : inventory.getFeatures()) {
    log.info("feature={} defaultEnabled={} patterns={}",
            f.getFeatureId(), f.isDefaultEnabled(), f.getPatterns());
}
for (SwitchInventory.GrayDescriptor g : inventory.getGrays()) {
    log.info("gray={} percentage={} forceGray={}", g.getPattern(), g.getPercentage(), g.isForceGray());
}
```

手动触发「指纹比对 + 重置」等同于重启后的那次执行：

```java
@Autowired
private ConsoleStartupResetter resetter;

resetter.run(null);   // 指纹不同则清空 console_feature_override / console_gray_override
```

### 5.2 SPI 扩展示例

**本模块没有 `META-INF/services` 文件**，不通过 JDK SPI 注册实现。
它对外只有两个扩展方向：

**（1）覆盖数据源替换：实现 `ApiSwitchOverrideProvider`（Spring Bean 方式）。**

接口全限定名：`com.chua.starter.common.support.api.feature.ApiSwitchOverrideProvider`
（来自 `spring-support-common-starter`，方法带 `default` 实现，返回 `null` 表示「无覆盖」）。

```java
@Bean
public ApiSwitchOverrideProvider myOverrideProvider() {
    return new ApiSwitchOverrideProvider() {
        @Override
        public FeatureOverride featureOverride(String featureId) {
            return null;   // 返回 null：回退注解 + FeatureManager 默认判定
        }

        @Override
        public GrayOverride grayOverride(String pattern) {
            return null;
        }
    };
}
```

生效方式：`ApiControlInterceptor` 与 `ApiFeatureConfiguration` 通过
`ObjectProvider<ApiSwitchOverrideProvider>` 取实现，所以**只要声明成 Spring Bean 就会被咨询**，
不需要在 `META-INF/services` 里登记。本模块自带的实现是
`ConsoleOverrideProvider`（读 SQLite 覆盖表），由 `ConsoleAutoConfiguration` 以 `@Bean` 注册。
拦截器用的是 `overrideProvider.getIfAvailable()`，容器里同时存在多个实现时需要给某个实现标
`@Primary`，否则会因无法唯一确定而装配失败。

**（2）环境注入扩展：`EnvironmentPostProcessor`。**
本模块登记在 `META-INF/spring.factories`：

```text
org.springframework.boot.env.EnvironmentPostProcessor=\
  com.chua.starter.console.environment.ConsoleKvEnvironmentPostProcessor
```

宿主若也要在环境准备阶段注入属性，按同样方式在自己的 `spring.factories` 里登记自己的
`EnvironmentPostProcessor` 实现即可，与本模块互不冲突（本模块顺序为
`Ordered.LOWEST_PRECEDENCE - 100`）。

## 六、与其它 starter 的关系

依赖方向（本模块 → 依赖）：

- `spring-support-oauth-client-starter` — 页面鉴权；它传递引入 `spring-support-common-starter`
  （`@Permission`、`ServiceInfoContributor`、`ApiSwitchOverrideProvider`、`ApiControlInterceptor`）
- `spring-boot-starter-web` + `spring-boot-starter-freemarker` — 控制器与页面渲染
- `org.xerial:sqlite-jdbc` — 自包含持久化，不依赖宿主的 `DataSource`

运行期的协作关系：

| 对方 | 关系 |
|---|---|
| `spring-support-common-starter` | 本模块提供 `ConsoleOverrideProvider`，被其 `ApiControlInterceptor` 在判定 `@ApiFeature` / `@ApiGray` 时优先咨询；无覆盖行时返回 `null`，行为与未装本模块一致 |
| `spring-support-oauth-client-starter` | 提供 `@Permission` 与 `PermissionInterceptor`；本模块页面/接口全部标注 `@Permission("console:manage")` |
| 父工程 | `pom.xml` 的 `<modules>` 收录本模块，`<dependencyManagement>` 管理其版本 |

仓库内**没有**其它 starter 依赖 `spring-support-console-starter`；
引入方式是宿主应用按需添加（与 `spring-support-osgi-starter` / `spring-support-bundle-starter`
这类「配套 starter」不同，本模块可独立使用）。

## 七、已知限制

1. **`plugin.console.permission-code` 目前不生效。** 控制器使用的是编译期常量
   `ConsolePageController.CONSOLE_PERMISSION = "console:manage"`，
   `@Permission(CONSOLE_PERMISSION)` 绑定的是常量，`ConsoleProperties#getPermissionCode`
   在源码中没有任何调用点。改配置项不能改变实际权限码。
2. **`plugin.console.kv-prefix` 目前不生效。** 页面调用的是 `store.listKv("")`，
   没有读取 `getKvPrefix()`；前缀过滤只能通过直接调用 `listKv(prefix)` 实现。
3. **键值注入只在启动时发生一次。** `ConsoleKvEnvironmentPostProcessor` 是
   `EnvironmentPostProcessor`，运行期 `setKv` 写入的值不会即时出现在 `Environment` 里，
   需重启（或用 `store.getKv(key)` 直接读库）。
4. **单连接同步访问。** `ConsoleSqliteStore` 持有一个 `Connection`，
   所有读写方法为 `synchronized`；高并发写会串行排队，且没有连接池/超时配置。
5. **指纹变化会清空覆盖。** `ConsoleStartupResetter` 发现指纹不同（注解增删改）
   就执行 `resetOverrides()`，清空 `console_feature_override` 与 `console_gray_override`；
   `console_kv` 属用户数据，不会被清空。
6. **首次启动没有覆盖库就跳过注入。** 库/表不存在时环境注入器静默 `return`
   （日志为 `debug`），不会建库；建库只发生在 `ConsoleSqliteStore` 构造时。
7. **鉴权依赖 oauth-client 的拦截器可用。** `@Permission` 由
   `AuthClientConfiguration#permissionProxyFactoryBean` 注册的 `PermissionInterceptor`
   执行；宿主若关闭/未装配该拦截器（或未提供登录态），注解不产生任何校验，
   `/console/**` 将处于无鉴权暴露状态，生产环境需自行补齐。
8. **无 CSRF 防护、无操作审计。** 调整接口是普通 `@PostMapping` + 表单参数，
   写入记录只带 `updated_at`（数据库默认值），不记录操作人。
