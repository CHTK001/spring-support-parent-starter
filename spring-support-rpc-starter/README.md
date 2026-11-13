# spring-support-rpc-starter

Spring Boot 自动配置的 RPC 门面：用 `@RpcService` 暴露服务、用 `@RpcResource` 注入远程代理，底层协议由 `plugin.rpc.type` 在 `RpcType` 中选择。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 服务暴露 | `RpcServiceBean`、`RpcServiceAnnotationBeanPostProcessor` | `@RpcService` |
| 服务引用 | `RpcResourceBean`、`RpcResourceAnnotationBeanPostProcessor` | `@RpcResource` |
| 包扫描 | `RpcClassPathBeanDefinitionScanner`、`ServicePackagesHolder` | `plugin.rpc.scan` |
| 协议适配 | `com.chua.common.support.network.rpc.RpcClient` / `RpcServer`（外部 SPI） | `plugin.rpc.type` |
| 代理创建 | `RpcProxyFactoryManager`、`JavassistRpcProxyFactory`、`JdkRpcProxyFactory` | 内部按 `getOrder()` 自动选择 |
| 调用拦截 | `RpcInterceptor`、`MetricsRpcInterceptor`、`LoggingRpcInterceptor` | Spring Bean，按 `getOrder()` 排序 |
| 负载均衡 | `RpcLoadBalancer`、`RandomLoadBalancer`、`RoundRobinLoadBalancer` | 仅提供实现类，当前调用链未消费 |
| 熔断与重试 | `RpcCircuitBreakerManager`、`RpcCircuitBreaker`、`RpcRetryPolicy` | 重试内部生效；熔断需自行声明 `RpcCircuitBreakerManager` Bean |
| 就近降级 | `SpringBeanRpcClient`、`SpringBeanRpcServer` | 同进程直调/降级，无网络 |
| 监控端点 | `RpcMonitorController` | `GET /v2/rpc/monitor/info`、`/connections`、`/metrics` |
| 服务信息 | `RpcAutoConfiguration#rpcServiceInfoContributor` | 服务信息注册表 |

自动配置登记文件 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 内容为：

```
com.chua.starter.rpc.support.configuration.RpcResourceAnnotationBeanPostProcessor
com.chua.starter.rpc.support.configuration.RpcServiceAnnotationBeanPostProcessor
com.chua.starter.rpc.support.configuration.RpcAutoConfiguration
```

> `RpcAutoConfiguration` 本身只注册服务信息贡献器；真正干活的是两个 `BeanPostProcessor` 与两个 `FactoryBean`。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-rpc-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用域 | 说明 |
|---|---|---|
| `utils-support-rpc-dubbo-starter` | compile | Dubbo 协议实现（pom 内 `dubbo.version` = `3.3.6`），随本模块传递引入 |
| `spring-support-common-starter` | compile | 服务信息、日志等公共能力 |

`@RpcService`、`@RpcResource`、`RpcType`、`RpcClient`、`RpcServer`、`RpcConsumerConfig` 等类型来自 `utils-support-common-starter`（包名 `com.chua.common.support.network.rpc`），不在本模块内定义。

## 三、配置项

前缀：`plugin.rpc`（`RpcProperties.PRE`）。

| key | 默认值 | 说明 |
|---|---|---|
| `plugin.rpc.enable` | `false` | 是否启用 RPC |
| `plugin.rpc.type` | `DUBBO` | 协议类型，取值见 `RpcType`：`DUBBO` / `SOFA` / `JSON` / `SPRINGBEAN` / `NONE` |
| `plugin.rpc.application-name` | `${spring.application.name:app}` | 应用名 |
| `plugin.rpc.scan` | 空 | 需要扫描 `@RpcService` 的包集合 |
| `plugin.rpc.access-key` | 空字符串 | 访问 AK，从配置注入，勿硬编码 |
| `plugin.rpc.secret-key` | 空字符串 | 访问 SK，从配置注入，勿硬编码 |
| `plugin.rpc.service-key` | 内置固定值 | 服务序列，生产环境必须覆盖 |
| `plugin.rpc.crypto` | `aes` | 加密算法 |
| `plugin.rpc.registry` | 空列表 | 注册中心列表（`RpcRegistryConfig`） |
| `plugin.rpc.protocols` | 空列表 | 协议列表（`RpcProtocolConfig`） |

### 消费者 `plugin.rpc.consumer`

`RpcConsumerConfig` 的字段多为包装类型且未赋初始值；`auto` 为 `true` 时会调用 `autoConfig()` 按 CPU 核数与堆大小补齐。

| key | 默认值 | 说明 |
|---|---|---|
| `auto` | `true` | 自动调优超时、连接数、重试间隔 |
| `timeout` | 未设置 | 调用超时（毫秒），调优后为 3000–15000 |
| `connect-timeout` | 未设置 | 连接超时（毫秒），调优后为 `timeout / 3` |
| `retries` | 未设置 | 失败重试次数 |
| `retry-enabled` / `retry-delay` | 未设置 | 是否重试 / 重试间隔（毫秒） |
| `load-balance` | 未设置 | `random`、`roundrobin`、`leastactive`、`consistenthash`、`shortestresponse` |
| `cluster` | 未设置 | `failover`、`failfast`、`failsafe`、`failback`、`forking`、`broadcast` |
| `async` / `invoke-type` | 未设置 | 异步开关 / 调用模式（`sync`、`async`、`future`、`callback`、`oneway`） |
| `version` / `group` | 未设置 | 默认服务版本 / 分组 |
| `connections` / `sticky` | 未设置 | 最大连接数 / 粘性连接 |
| `serialization` | 未设置 | 序列化协议（`hessian` 为本模块常量默认值） |
| `check` / `lazy` | 未设置 | 启动检查 / 延迟检查 |
| `inline` | 未设置 | 同 JVM 直调，命中本地已注册服务时跳过网络 |
| `access-log` | 未设置 | 是否记录访问日志 |
| `url` | 未设置 | 直连地址 |
| `token` | 未设置 | 服务治理令牌（请求头 `X-RPC-Token`） |
| `interface-class` / `interface-name` | 未设置 | 服务接口 |

### 协议 `plugin.rpc.protocols[]`（`RpcProtocolConfig` record）

`name`、`host`、`port`、`payload`（默认 8MB）、`buffer`、`threads`、`accepts`、`io-threads`、`alive`、`queues`、`serialization`、`codec`、`transporter`、`dispatcher`、`threadpool`、`heartbeat`、`ssl`、`register`、`charset`、`keep-alive`、`core-threads`、`max-threads`、`idle-timeout`。
可用 `RpcProtocolConfig.auto(name, port)` 生成一份按机器规格调优的协议配置。

### 注册中心 `plugin.rpc.registry[]`（`RpcRegistryConfig`）

`address`、`username`、`password`、`port`、`protocol`（`zookeeper`、`nacos`、`redis`、`multicast`、`direct`）、`timeout`、`session-timeout`、`group`、`version`、`check`、`dynamic`、`register`、`subscribe`、`weight`、`parameters`、`file`。

## 四、快速开始

1. 引入 `spring-support-rpc-starter`（Dubbo 实现随依赖传递引入）。
2. 打开开关并选择协议：

   ```yaml
   plugin:
     rpc:
       enable: true
       type: DUBBO
       application-name: order-service
       registry:
         - address: zookeeper://localhost:2181
           timeout: 3000
       protocols:
         - name: dubbo
           host: 0.0.0.0
           port: 20880
       consumer:
         timeout: 3000
         retries: 2
         load-balance: random
   ```

3. 提供方加 `@RpcService`，消费方加 `@RpcResource`，两端接口全限定名与 `version` / `group` 必须一致。

## 五、运行示例

### 5.1 调用示例

本模块无链式 API：`RpcServiceBean` / `RpcResourceBean` 是 `FactoryBean`，配置只能通过 `RpcProperties` 与注解属性表达，没有 `Builder` 或 `return this` 风格入口。

```java
// 1) 提供方：实现类上标注，接口可从实现的第一个接口推断
@RpcService(interfaceClass = UserService.class, version = "1.0.0", group = "default")
public class UserServiceImpl implements UserService {
    @Override
    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }
}

// 2) 消费方：字段注入远程代理，注解属性优先于 plugin.rpc.consumer 全局配置
@Service
public class OrderService {

    @RpcResource(version = "1.0.0", timeout = 3000)
    private UserService userService;

    public void createOrder(Long userId) {
        User user = userService.getUserById(userId);
    }
}
```

运行期可查询（需自行把 `RpcMonitorController` 注册为 Bean，见第七节）：

- `GET /v2/rpc/monitor/info` — 服务端信息与服务列表
- `GET /v2/rpc/monitor/connections` — 连接信息
- `GET /v2/rpc/monitor/metrics` — 方法级调用指标

### 5.2 SPI 扩展示例

本模块的扩展点是 Spring Bean，不是 `META-INF/services`：实现 → 声明为 Bean → 由 `RpcResourceBean` / `SpringRpcInvocationHandler` 按 `getOrder()` 注入调用链。

```java
// 1) 实现：四个回调都有默认空实现，按需覆盖
public class TraceRpcInterceptor implements RpcInterceptor {

    @Override
    public int getOrder() {
        return -100;   // 数字越小越先执行
    }

    @Override
    public boolean preHandle(Method method, Object[] args) {
        // 返回 false 表示拦截本次调用
        return true;
    }

    @Override
    public Object postHandle(Method method, Object[] args, Object result) {
        // 可替换返回值
        return result;
    }

    @Override
    public void handleException(Method method, Object[] args, Throwable error) throws Throwable {
        throw error;
    }

    @Override
    public void afterCompletion(Method method, Object[] args) {
        // 无论成功失败都会执行，适合埋点上报
    }
}

// 2) 登记为 Bean
@Bean
public RpcInterceptor traceRpcInterceptor() {
    return new TraceRpcInterceptor();
}

// 3) 生效：@RpcResource 代理的每次远程调用都会按 getOrder() 顺序穿过拦截器链
```

同类扩展点还有：

| 扩展点 | 登记方式 | 生效位置 |
|---|---|---|
| `RpcInterceptor` | Spring Bean | 远程调用前后（`SpringRpcInvocationHandler` 构造时收集） |
| `RpcCircuitBreakerManager` | Spring Bean | 远程调用熔断包装；未声明则跳过熔断 |
| `RpcProxyFactory` | `RpcProxyFactoryManager.getInstance().registerFactory(factory)` | 代理对象创建 |
| `RpcLoadBalancer` | 仅实现类，无消费入口 | 当前版本未接入调用链 |

## 六、与其它 starter 的关系

- `spring-support-common-starter`：服务信息、日志基础设施，本模块必需。
- `utils-support-common-starter`：`@RpcService` / `@RpcResource` / `RpcType` / `RpcClient` / `RpcServer` 的定义处，协议实现的 SPI 宿主。
- `utils-support-rpc-dubbo-starter`：唯一随本模块传递引入的协议实现。
- `spring-support-sync-starter`：同为"端到端通道"，但定位是消息/数据同步，不提供 `@RpcService` 式方法级远程调用，两者互不替代。
- `spring-support-job-starter`：任务调度走 `SyncClient` / `SyncServer`，与 RPC 无装配依赖。

## 七、已知限制

- `RpcType` 只有 `DUBBO`、`SOFA`、`JSON`、`SPRINGBEAN`、`NONE`；**不存在 HTTP、Armeria 协议枚举**，早期文档中的这两项不成立。
- 只有 Dubbo 随本模块引入；`SOFA`、`JSON` 需要自行追加 `utils-support-rpc-sofa-starter` / `utils-support-rpc-json-starter`，否则 `type` 指向的实现取不到。utils 层 `RpcClient` / `RpcServer` 的 SPI 键为 `native`（common）、`dubbo`、`json`、`sofa`、`zmq`；`SPRINGBEAN` 不走 SPI，由本模块直接构造 `SpringBeanRpcServer` / `SpringBeanRpcClient`。
- `RpcMonitorController` 虽带 `@RestController`，但**不在** `AutoConfiguration.imports` 中、也没有 `@Component`；宿主需自行 `@Bean` 注册后才能访问 `/v2/rpc/monitor/*`。
- `LoggingRpcInterceptor`、`MetricsRpcInterceptor` 同样不会被自动装配，需要自行声明 Bean；没有声明时监控端点里的方法级指标为空。
- `RandomLoadBalancer` / `RoundRobinLoadBalancer` 只提供实现类，**当前版本没有调用链读取 `RpcLoadBalancer` Bean**，`consumer.load-balance` 由底层协议实现自行解释。
- `RpcCircuitBreakerManager` 需要自行声明 Bean 才会参与调用（`SpringRpcInvocationHandler` 取不到时仅记 debug 日志）；`RpcRetryPolicy` 则由 `RpcConsumerConfig` 在创建 handler 时内部构造。
- `plugin.rpc.consumer` 的多数字段是可空包装类型，未配置时依赖 `autoConfig()` 或底层协议实现的缺省值，不要假设固定默认值。
- `service-key` 自带内置默认值，`access-key` / `secret-key` 默认为空字符串；三者都属于敏感配置，生产必须在外部配置中心覆盖。
