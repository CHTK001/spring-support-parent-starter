# spring-support-configcenter-starter

在 Spring 环境准备阶段从远程配置中心（Nacos / Apollo / ZooKeeper / Redis）加载配置进 `Environment`，并让 `@ConfigValue`、`@Value` 注入的字段支持热更新。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 配置加载 | `ConfigCenterConfigurationEnvironmentPostProcessor`（`EnvironmentPostProcessor`，`Ordered` = `LOWEST_PRECEDENCE - 100`） | `META-INF/spring.factories` |
| 配置实例持有 | `ConfigCenterHolder`（全局单例，`@PreDestroy` 时 `shutdown()`） | 后置处理器写入，后续复用 |
| `@ConfigValue` 注入与热更新 | `ConfigValueBeanPostProcessor` | `ConfigValueAutoConfiguration`（`plugin.config-center.enable=true`） |
| `@Value` 注入与热更新 | `ValueAnnotationBeanPostProcessor` | 同上 |
| 自动配置装配 | `ConfigValueAutoConfiguration` | `META-INF/spring/...AutoConfiguration.imports` |
| 模块环境登记 | `ModuleEnvironmentRegistration`（前缀 `plugin.config-center`） | `ConfigValueAutoConfiguration` |
| 协议扩展点 | `com.chua.common.support.config.center.ConfigCenter`（SPI） | `META-INF/extensions/...ConfigCenter` |
| 服务信息登记 | `ServiceInfoContributor`（标志 `configcenter`，另有 `config-center` / `config-hot-reload` 运行标志） | `ServiceInfoRegistry` |

启动期实际发生的事（源码事实）：

1. `EnvironmentPostProcessor` 读取 `plugin.config-center.*`，未 `enable` 直接返回（并在服务信息里登记为关闭）；
2. `ServiceProvider.of(ConfigCenter.class).getNewExtension(protocol, ConfigCenterSetting)` 创建实现，`null` 时打「暂不支持」并退出，不抛异常；
3. `configCenter.start()` 后存入 `ConfigCenterHolder`，再按固定优先级把配置写入 `PropertySources`；
4. `hot-reload.enabled=true` 且实现 `isSupportListener()` 为 `true` 时，为每个已加载的 dataId 注册 `EnvironmentConfigListener`；
5. Bean 阶段由两个 `BeanPostProcessor` 扫描注解、注入初值、按需注册监听。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-configcenter-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 说明 |
|---|---|---|
| `spring-support-common-starter` | `ServiceInfoRegistry`、`ModuleEnvironmentRegistration` | 必需 |
| `utils-support-nacos-starter` | Nacos 的 `ConfigCenter` 实现（`@Spi("nacos")`） | 必需（pom 直接声明） |

配置中心的可选协议实现（不在本模块 pom 内，需要时由宿主自行引入）：

| `protocol` | 实现类 | 需要引入 |
|---|---|---|
| `nacos` | `NacosConfigCenter` | `utils-support-nacos-starter`（已传递） |
| `apollo` | `ApolloConfigCenter` | `utils-support-apollo-starter` |
| `zookeeper` | `ZookeeperConfigCenter` | `utils-support-zookeeper-starter` |
| `redis` | `RedisConfigCenter` | `utils-support-redis-starter` |

## 三、配置项

前缀 `plugin.config-center`，配置类 `ConfigCenterProperties`（`@ConfigurationProperties`，`ignoreInvalidFields = true`）。

| key | 默认值 | 说明 |
|---|---|---|
| `enable` | `false` | 总开关。`false` 时后置处理器直接返回，且 `ConfigValueAutoConfiguration` 整体不装配 |
| `protocol` | 无 | 协议名，对应 `ConfigCenter` SPI 名 |
| `address` | 无 | 配置中心地址 |
| `username` | 无 | 账号 |
| `password` | 无 | 密码 |
| `namespace-id` | 无 | 命名空间；为空时使用 `spring.profiles.active`（作为 `ConfigCenterSetting#profile`） |
| `connect-timeout` | `5000` | 连接超时（毫秒） |
| `read-timeout` | `5000` | 读取超时（毫秒） |
| `hot-reload.enabled` | `true` | 是否注册配置变更监听 |
| `hot-reload.value-annotation-enabled` | `true` | 是否让 `@Value` 字段支持热更新 |
| `hot-reload.config-value-annotation-enabled` | `true` | 是否让 `@ConfigValue` 字段支持热更新 |
| `hot-reload.refresh-delay-ms` | `100` | 防抖窗口：同键在该时间内频繁变更只处理一次 |
| `hot-reload.log-on-change` | `true` | 变更时打印日志 |

**配置加载优先级**（`ConfigCenterConfigurationEnvironmentPostProcessor#loadConfigurations`，从高到低）：

| 顺序 | dataId | 条件 |
|---|---|---|
| 1 | `Application-{spring.application.name}-{spring.profiles.active}` | application.name 与 active 都非空 |
| 2 | `Application-{spring.application.name}` | application.name 非空 |
| 3 | `application-{include}-{active}.yml` | `spring.profiles.include` 中每一项，active 非空 |
| 4 | `application-{include}.yml` | `spring.profiles.include` 中未被第 3 步命中的项 |

所有配置以 `OriginTrackedMapPropertySource` 追加到 `addLast`，即**远程配置不会覆盖本地 `application.yml`**；同层之间按上面的顺序先加载者先入列。

## 四、快速开始

**第一步**：引入本模块，选定协议并补齐对应实现（默认已带 Nacos）。

**第二步**：打开开关并配置连接：

```yaml
plugin:
  config-center:
    enable: true
    protocol: nacos
    address: http://127.0.0.1:8848
    username: nacos
    password: nacos
    namespace-id: dev
    hot-reload:
      enabled: true
      refresh-delay-ms: 200
```

**第三步**：在配置中心按优先级命名 dataId（如 `Application-my-app-dev`），内容用普通 YAML；应用启动即加载，业务代码用 `@Value` / `@ConfigValue` 读取即可。

## 五、运行示例

### 5.1 调用示例

本模块无链式 API，全部为「声明注解 + 配置开关」的步骤式用法。

**`@ConfigValue`（推荐：支持默认值、热更新与变更回调）**：

```java
@Component
public class MyService {

    /** hotReload=true 才会注册监听；不写则为一次性注入 */
    @ConfigValue(value = "${custom.config.key}", defaultValue = "defaultValue", hotReload = true)
    private String configValue;

    @ConfigValue(value = "${app.timeout}", defaultValue = "5000", hotReload = true, callback = "onConfigChange")
    private Integer timeout;

    /** 回调签名固定为 (String key, Object oldValue, Object newValue) */
    public void onConfigChange(String key, Object oldValue, Object newValue) {
        log.info("配置变更: key={}, old={}, new={}", key, oldValue, newValue);
    }
}
```

`@ConfigValue` 的定义在 `utils-support-common-starter`（`com.chua.common.support.objects.annotation.ConfigValue`），只有四个属性：`value`、`defaultValue`、`hotReload`（默认 `false`）、`callback`（默认空）。`value` 为空时按字段名推断键。

**`@Value`（无需 `@RefreshScope`）**：

```java
@Component
public class MyService {

    @Value("${custom.config.key:defaultValue}")
    private String configValue;
}
```

由 `ValueAnnotationBeanPostProcessor` 在 `postProcessAfterInitialization` 阶段接管：先查本地缓存，再回源 `Environment`，命中后写入缓存并按需注册监听。**不要再用 `@RefreshScope`**——该注解属于 Spring Cloud 的刷新机制，与这里的字段回写是两套链路。

**传统 `@ConfigurationProperties`** 仍然可用（远程配置已在 `Environment` 里）：

```java
@ConfigurationProperties(prefix = "custom.config")
public record CustomConfig(String key) {
}
```

**生效条件汇总**：`enable=true` → 协议实现存在 → 该 dataId 已被加载 → `hot-reload.enabled=true` → 对应注解开关为 `true` → `ConfigCenter#isSupportListener()` 为 `true`。任一不满足时字段只在启动时注入一次。

### 5.2 SPI 扩展示例

扩展点：`com.chua.common.support.config.center.ConfigCenter`，通过 `ServiceProvider#getNewExtension(protocol, ConfigCenterSetting)` 创建，构造方法须接收 `ConfigCenterSetting`。

**实现**（继承 `AbstractConfigCenter` 可复用监听器管理与启停日志，只需实现 `get(dataId)` / `get(dataId, group)`）：

```java
@Spi("mycenter")
public class MyConfigCenter extends AbstractConfigCenter {

    public MyConfigCenter(ConfigCenterSetting setting) {
        super(setting);
    }

    @Override
    public Map<String, Object> get(String dataId) {
        // 从自有存储读取该 dataId 的键值，解析成扁平 Map
        return Map.of();
    }

    @Override
    public Map<String, Object> get(String dataId, String group) {
        return get(dataId);
    }

    @Override
    public void start() { logStartup(); }

    @Override
    public void close() { logShutdown(); }
}
```

**登记**：在自有 jar 下新建 `META-INF/extensions/com.chua.common.support.config.center.ConfigCenter`，一行一条 `名称=实现类全限定名`（与 Nacos 模块同格式）：

```text
mycenter=com.example.config.MyConfigCenter
```

**生效**：配置 `plugin.config-center.protocol: mycenter`。启动时后置处理器按该协议创建实例并放进 `ConfigCenterHolder`；`ConfigValueAutoConfiguration` 的 `configCenter()` Bean 会直接复用它（日志「复用已创建的 ConfigCenter 实例」）。

## 六、与其它 starter 的关系

- **上游**：`spring-support-common-starter`（`ServiceInfoRegistry`、`ModuleEnvironmentRegistration`、`SpringBeanUtils`）。
- **协议实现方**：`utils-support-nacos-starter` / `utils-support-apollo-starter` / `utils-support-zookeeper-starter` / `utils-support-redis-starter`，本模块只消费其 `ConfigCenter` SPI 实现。
- **与 `spring-support-common-starter` 的配置来源机制**：远程配置被追加到 `Environment` 末尾，因此不会压过本地 YAML；框架其它模块读配置走同一个 `Environment`，无需感知本模块。
- **本仓库内无直接消费者**：没有其它 starter 依赖本模块；`spring-support-api-starter` 的 pom 只在 `dependencyManagement` 中登记了版本，供上层应用按需引入。
- **注解归属**：`@ConfigValue` 来自 `utils-support-common-starter`，本模块只提供处理器。

## 七、已知限制

1. **默认关闭**：`plugin.config-center.enable` 默认 `false`，不开启时既没有远程配置，也没有 `@ConfigValue` 处理器（`ConfigValueAutoConfiguration` 带 `@ConditionalOnProperty`）。
2. **只带 Nacos**：pom 仅声明 `utils-support-nacos-starter`，`apollo` / `zookeeper` / `redis` 需宿主自行引入实现模块，否则启动日志只会出现「暂不支持 {protocol}」。
3. **远程配置优先级低于本地**：所有远程 `PropertySource` 都以 `addLast` 追加，本地 `application.yml` 里同名的 key 会赢；想让某键由配置中心决定，本地必须不写。
4. **只加载四类 dataId**：dataId 命名完全由代码拼装（`Application-*` 与 `application-*.yml`），加载链路只调用 `ConfigCenter#get(dataId)`，因此不支持自定义 dataId、也不支持指定 group，更没有 `spring.cloud.config` 那套约定。
5. **热更新依赖实现能力**：`isSupportListener()` 为 `false` 时只打印「当前配置中心不支持监听功能」，字段不会更新；`@ConfigValue` 未写 `hotReload=true` 同样不更新。
6. **回调失败不影响注入**：回调方法不存在或抛异常只记日志，字段值已写入。
7. **防抖是「丢弃」而非「合并」**：`refresh-delay-ms` 窗口内同键的后续变更直接跳过（`EnvironmentConfigListener#onUpdate` 与 `ConfigValueListener#onUpdate` 都是提前 return），被丢掉的中间值不会补一次。
8. **没有配置加密 / 版本管理 / 回滚 / 审计**：这些能力本模块不提供；`AbstractConfigCenter#publish` 默认实现直接返回 `false` 并打 WARN（`isSupportPublish()` 却默认 `true`），调用发布前需确认实现真的覆盖了 `publish`。
9. **本 starter 不调用 `publish` / `publishIfAbsent`**：`@ConfigValue` 没有推送相关属性，注解上的四个属性见第 5.1 节。
10. **`namespace-id` 语义随实现而定**：它被传给 `ConfigCenterSetting#profile`，Nacos 侧当命名空间用；为空时回落 `spring.profiles.active`。

---

本项目采用 Apache License 2.0 许可证。
