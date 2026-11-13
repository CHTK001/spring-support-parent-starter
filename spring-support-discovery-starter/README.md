# spring-support-discovery-starter

把 `ServiceDiscovery` SPI 的多协议实现装配成 Spring Bean：按配置自动创建发现客户端、注册本机服务节点、订阅变更，并对外暴露统一查询门面 `DiscoveryService`。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 配置绑定 | `DiscoveryListProperties` | `plugin.discovery` 前缀 |
| 注册编排 | `DiscoveryConfiguration`（`BeanDefinitionRegistryPostProcessor`） | `META-INF/spring/...AutoConfiguration.imports` |
| 客户端生命周期 | `DiscoveryConfiguration.ServiceDiscovertyFactoryBean`（`initMethod=start`、`destroyMethod=close`） | 由注册编排按协议创建 |
| 统一查询门面 | `DiscoveryService`（Bean 名 `discoveryService#embedd`） | 注入即可用 |
| 协议实现路由 | Bean 名 `serviceDiscovery#<protocol>`（重名追加 `#0`/`#1`） | `DiscoveryService#resolveServiceDiscovery` |
| 环境元数据合并 | `DiscoveryEnvironment`（`getName()` 等于节点的 `namespace` 时合并 `getProperties()`） | 声明 Bean |
| 负载均衡策略 | `defaultBalance`，默认 `weight`，可用 `-Dchua.discovery.balance` 覆盖 | `DiscoveryService#setDefaultBalance` |
| 协议扩展点 | `com.chua.common.support.network.discovery.ServiceDiscovery`（SPI） | `META-INF/extensions/...ServiceDiscovery` |
| 服务信息登记 | `ServiceInfoContributor`（标志 `discovery`） | 启动横幅 / `/actuator/info` |
| 开关关闭时的降级 | `DefaultServiceDiscovery`（空实现） | `plugin.discovery.enable=false` |

关键行为（源码事实）：

- `plugin.discovery.enable=false`（默认）时只注册一个空实现的 `ServiceDiscovery`，不连任何注册中心；
- 开启后逐个处理 `properties` 列表，`enabled=true` 的条目才创建客户端；全部不可用时告警并回退默认实现；
- 每个 `node` 生成一个 `Discovery`：`id=serverId`（未配置时用 `Project.calcApplicationUuid()`），`serverId=md5(host+port)`，`uriSpec=namespace`；
- `start()` 依次 `serviceDiscovery.start()` → `registerService(namespace, discovery)` → 支持订阅时 `subscribe(...)`；
- `close()` **先反注册再断连**，避免滚动重启期间实例残留到 TTL 剔除（见 `DiscoveryLifecycleTest`）。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-discovery-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用 | 说明 |
|---|---|---|
| `spring-support-common-starter` | `Project`、`ServiceInfoContributor`、`SpringBeanUtils`、`ModuleLog` | 必需（pom 中唯一编译依赖） |
| `org.junit.jupiter:junit-jupiter` | 注册/反注册、同协议多实例解析回归 | `test` 作用域 |

本模块 pom **不引入任何注册中心客户端**，协议能力由宿主 classpath 上的 utils 模块决定：

| `protocol` 取值 | 实现类 | 需要引入 |
|---|---|---|
| `nacos` | `NacosServiceDiscovery` | `utils-support-nacos-starter` |
| `zookeeper` | `ZookeeperServiceDiscovery` | `utils-support-zookeeper-starter` |
| `redis` | `RedisServiceDiscovery` | `utils-support-redis-starter` |
| `apollo` | `ApolloServiceDiscovery` | `utils-support-apollo-starter` |
| `udp-broadcast` | `UdpBroadcastServiceDiscovery` | `utils-support-common-starter`（已在 classpath） |

未引入对应实现时，`protocol` 会解析为 `null`，日志输出「未发现可用的服务实现」并注册 `DefaultServiceDiscovery`，启动不失败但注册/查询都是空操作。

## 三、配置项

前缀 `plugin.discovery`，配置类 `DiscoveryListProperties`；列表元素为 `DiscoveryProperties`，其 `node` 列表元素为 `DiscoveryNodeProperties`。

| key | 默认值 | 说明 |
|---|---|---|
| `enable` | `false` | 总开关。`false` 时只注册空实现 `DefaultServiceDiscovery` |
| `properties` | `[]` | 多发现中心配置列表 |
| `properties[].enabled` | `true` | 单条配置是否启用 |
| `properties[].protocol` | 无 | 协议名，对应 `ServiceDiscovery` SPI 名（`nacos` / `zookeeper` / `redis` / `apollo` / `udp-broadcast`） |
| `properties[].address` | 无 | 注册中心地址 |
| `properties[].username` | 无 | 账号，装入 `DiscoveryOption#setUser` |
| `properties[].password` | 无 | 密码 |
| `properties[].connection-timeout-millis` | `10000` | 连接超时（毫秒） |
| `properties[].session-timeout-millis` | `10000` | 会话超时（毫秒） |
| `properties[].node` | `[]` | 要注册的服务节点列表 |
| `properties[].node[].server-id` | 无 | 实例 ID；为空时用 `Project.calcApplicationUuid()` |
| `properties[].node[].namespace` | 无 | 服务命名空间，即注册/查询用的 `uriSpec`；也是匹配 `DiscoveryEnvironment#getName()` 的键 |
| `properties[].node[].protocol` | `http` | 节点通信协议，写入 `Discovery#protocol` |

另有 JVM 参数 `-Dchua.discovery.balance=<strategy>` 设置默认负载均衡策略，可取值来自 `LoadBalance` SPI：`weight`（默认）、`random`、`round` / `polling`。

## 四、快速开始

**第一步**：引入本模块与所用注册中心的 utils 实现（例如 Nacos）。

**第二步**：打开开关并声明发现中心与节点：

```yaml
plugin:
  discovery:
    enable: true
    properties:
      - protocol: nacos
        address: http://localhost:8848
        username: nacos
        password: nacos
        connection-timeout-millis: 10000
        session-timeout-millis: 10000
        node:
          - server-id: ${spring.application.name}
            namespace: /demo-service
            protocol: http
```

**第三步**：启动应用。注册在 Bean 初始化阶段完成，无需写任何注册代码；业务侧注入 `DiscoveryService` 查询即可。

## 五、运行示例

### 5.1 调用示例

本模块无链式 API：`DiscoveryService` 的四个方法都直接返回查询结果。以下为步骤式用法。

**按默认协议查询**（默认协议 = `primary` 的那个 `ServiceDiscovery`，即配置列表里第一条成功创建的）：

```java
@Service
@RequiredArgsConstructor
public class DemoService {

    private final DiscoveryService discoveryService;

    /** 单个实例：内部按默认负载均衡策略（weight）选一个 */
    public Discovery getOne(String uriSpec) {
        return discoveryService.getDiscovery(uriSpec);
    }

    /** 全部实例 */
    public Set<Discovery> getAll(String uriSpec) {
        return discoveryService.getDiscoveryAll(uriSpec);
    }
}
```

**按指定协议查询**（多注册中心并存时）：

```java
Discovery fromNacos = discoveryService.getDiscovery("nacos", "/demo-service");
Set<Discovery> allFromZk = discoveryService.getDiscoveryAll("zookeeper", "/demo-service");
```

协议解析顺序：Bean 名等于 `serviceDiscovery#<protocol>` → Bean 名以 `#<protocol>` 结尾 → 以 `serviceDiscovery#<protocol>#` 开头（同协议多实例的重名后缀）→ 全部未命中回退默认实现。

**切换默认负载均衡策略**：

```java
discoveryService.setDefaultBalance("random");   // 可用值：weight / random / round(polling)
```

**`Discovery` 主要字段**：

```text
id         实例唯一标识（配置的 serverId 或应用 UUID），反注册用它
serverId   md5(host + port) 地址指纹，端口变化即变化，不要拿它当反注册键
uriSpec    命名空间
host/port  本机地址与端口
protocol   节点协议，默认 http
metadata   Project 元数据 + DiscoveryEnvironment 合并的属性
weight     负载均衡权重
timeout    请求超时（毫秒）
scatterId  业务分组，仅同组节点参与同一负载均衡池
```

**直接操作 SPI（可选）**：容器里的 `ServiceDiscovery` Bean 也可注入。`registerService` / `unregisterService` / `updateService` 返回 `this`，支持链式调用：

```java
@Autowired
private ServiceDiscovery nacosServiceDiscovery;

nacosServiceDiscovery
        .registerService("/demo-service", discovery)
        .updateService("/demo-service", discovery);
```

**手工构造实例**（`Discovery` 带 `@Builder`，配置驱动之外的补充注册场景）：

```java
Discovery discovery = Discovery.builder()
        .id("demo-node-1")              // 反注册键，实例生命周期内稳定
        .uriSpec("/demo-service")       // 命名空间
        .host("127.0.0.1")
        .port(8080)
        .protocol("http")
        .weight(1.0d)
        .build();

serviceDiscovery.registerService(discovery.getUriSpec(), discovery);
```

### 5.2 SPI 扩展示例

扩展点：`com.chua.common.support.network.discovery.ServiceDiscovery`，实现被 `ServiceProvider#getNewExtension(protocol, DiscoveryOption)` 创建，因此**必须提供接收 `DiscoveryOption` 的构造方法**。

**实现**：

```java
@Spi("myproto")
public class MyServiceDiscovery extends AbstractServiceDiscovery {

    public MyServiceDiscovery(DiscoveryOption option) {
        super(option);
    }

    @Override
    public void start() { /* 建连 */ }

    @Override
    public Set<Discovery> getServiceAll(String path) { /* 查询列表 */ }
}
```

**登记**：在自己的 jar 下新建 `META-INF/extensions/com.chua.common.support.network.discovery.ServiceDiscovery`，内容为 `名称=实现类全限定名`（与 nacos 模块的登记文件同格式）：

```text
myproto=com.example.discovery.MyServiceDiscovery
```

**生效**：把该 jar 放进宿主 classpath，配置里写 `protocol: myproto` 即可；`DiscoveryConfiguration` 会在启动时按该协议创建 Bean，Bean 名为 `serviceDiscovery#myproto`。

## 六、与其它 starter 的关系

- **上游**：`spring-support-common-starter`（`Project`、`ServiceInfoContributor`、`ServiceInfoRegistry`、`SpringBeanUtils`）。
- **协议实现**：`utils-support-nacos-starter` / `utils-support-zookeeper-starter` / `utils-support-redis-starter` / `utils-support-apollo-starter`，本模块只消费其 SPI 实现。
- **被依赖**：`spring-support-monitor-starter`（`optional`，为「服务管理 → service-discovery 插件」提供 `/ms/monitor/discovery` 查询；缺失时该接口降级为「未启用发现服务」）、`spring-support-report-client-starter`。
- **无反向依赖**：本模块不依赖 rpc / web / 缓存等上层 starter。

## 七、已知限制

1. **默认关闭**：`plugin.discovery.enable` 默认 `false`，不配置就等于没有服务发现，且只存在空实现 Bean。
2. **协议不是内置的**：pom 只带 `spring-support-common-starter`，nacos/zookeeper/redis/apollo 需宿主自行引入对应的 utils starter；实现缺失时降级为空操作而非启动失败。
3. **`serverId` 会随端口变化**：它是 `md5(host + port)`，改端口会留下孤儿注册；反注册请用 `id` 或传整个 `Discovery`。
4. **同协议多实例只有一个可用**：重名时 Bean 名为 `serviceDiscovery#<protocol>#0`，`DiscoveryService` 按前缀匹配只返回**第一个**，其余实例只能通过按 Bean 名注入访问。
5. **环境元数据合并时机**：`DiscoveryEnvironment` 的属性在 `ServiceDiscovertyFactoryBean#start()` 才合并（`BeanDefinitionRegistryPostProcessor` 阶段合并会强制提前实例化业务 Bean）。因此 `namespace` 必须与 `DiscoveryEnvironment#getName()` 完全相等才会合并。
6. **订阅只打日志**：内置的 `ServiceDiscoveryListener` 仅记录「服务变更」，不维护本地实例缓存，也不通知业务方；需要变更驱动时请自行调用 `subscribe` 注册监听器。
7. **无健康检查实现**：健康检查与故障剔除由具体 `ServiceDiscovery` 实现决定，本模块不提供统一的探活/剔除策略。
8. **旧版本说法已作废**：本模块没有 Consul / Eureka 实现，也不内置「配置版本管理」「服务管理接口 `ServiceController`」等能力，相关描述已从本文移除。

---

本项目采用 Apache License 2.0 许可证。
