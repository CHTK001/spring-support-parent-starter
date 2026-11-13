# spring-support-common-starter

Spring Support 体系的通用底座：统一响应包装、接口版本/灰度/功能开关、验证码、SPI 选项、服务信息横幅与一批通用工具类。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 统一响应包装 | `ApiUniformResponseBodyAdvice` | `plugin.api.uniform`（默认 `true`） |
| 响应加密 / 请求解密 | `ApiProperties.ResponseEncodeProperties`、`RequestDecodeProperties` | `plugin.api.encode` / `plugin.api.decode` |
| 接口版本路由 | `ApiVersion`、`ApiVersionRequestMappingHandlerMapping` | `@ApiVersion` |
| 灰度发布 | `ApiGray`、`ApiGrayEvaluator` | `@ApiGray`、`plugin.api.gray` |
| 功能开关 | `ApiFeature`、`ApiFeatureManager`、`ApiFeatureController` | `@ApiFeature`、`plugin.api.feature` |
| 内部接口管控 | `ApiInternal` | `@ApiInternal`、`plugin.api.internal` |
| 废弃接口提示 | `ApiDeprecated` | `@ApiDeprecated`、`plugin.api.deprecated` |
| 接口 Mock | `ApiMock` | `@ApiMock`、`plugin.api.mock` |
| 图形验证码 | `CaptchaProvider`、`CaptchaProperties` | `GET /v1/captcha/captcha` |
| SPI 选项浏览 | `OptionalProvider` | `GET /v1/option/**`、`plugin.optional` |
| 服务信息注册表 | `ServiceInfoRegistry`、`ServiceInfoContributor` | `GET /service-info`、`plugin.service-info` |
| 方法追踪 | `Watch`、`TrackContext` | `@Watch` |
| 模块日志格式化 | `ModuleLog` | 代码调用 |
| 媒体类型处理 | `MediaTypeHandler`（JSON / XML / CSV 实现） | SPI，`META-INF/extensions` |
| Actuator 保护 | `ActuatorProperties`、`ActuatorConfiguration` | `plugin.actuator` |
| 异步线程池 | `AsyncThreadPoolConfiguration`、`AsyncThreadPoolProperties` | `plugin.thread` |
| Jackson 定制 | `JacksonConfiguration`、`JacksonProperties` | `plugin.jackson` |
| 请求指纹 | `RequestFingerprintAutoConfiguration` | `plugin.security.fingerprint` |

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-common-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

版本与父 POM `spring-support-parent-starter` 保持一致（当前 `4.0.0.51`），实际引入时建议由父工程或 BOM 统一托管，无需手写 `<version>`。

关键依赖：

| 依赖 | 作用 | 说明 |
|---|---|---|
| `utils-support-common-starter` | `ReturnResult`、`Action`、SPI 基础设施 | 编译期必需 |
| `utils-support-spring-starter` | Spring 工具类 | 必需 |
| `utils-support-httpclient-starter` | HTTP 调用与 TraceId 透传 | 必需 |
| `spring-boot-starter-validation` | Bean Validation | 必需 |
| `spring-boot-starter-cache` | 缓存抽象（不含具体实现） | 缓存实现由 `cache-starter` / `redis-starter` 提供 |
| `easy-captcha` / `utils-support-captcha-starter` | 图形验证码、第三方打码 | 已在 pom 中引入 |
| `spring-boot-webmvc` | `WebMvcRegistrations` 扩展点 | Boot 4.x 独立模块 |

`spring-boot-starter-webflux`、`tomcat-embed-core`、`undertow-core`、`okhttp`、`ip2proxy-java` 均为 `provided`，由宿主提供。

## 三、配置项

`@ConfigurationProperties` 按前缀分组，均带 `ignoreInvalidFields = true`：

| 前缀 | 配置类 | 关键项（默认值） |
|---|---|---|
| `plugin.api` | `ApiProperties` | `uniform`=`true`；`version.enable`=`true`；`platform.enable`=`true`、`platform.name`=`system`；`encode.enable`=`false`、`encode.codecType`=`aes`；`decode.enable`=`false`、`decode.reject-on-decode-failure`=`false`；`spi.enable`=`true`；`mock.enable`=`false`、`mock.profiles`=`dev,test`；`feature.enable`=`false`、`feature.path`=`/api/features`；`gray.enable`=`true`、`gray.header-name`=`X-Gray-Hit`；`internal.enable`=`true`；`deprecated.enable`=`true`、`deprecated.add-warning-header`=`true`；`ignore-format-packages` |
| `plugin.captcha` | `CaptchaProperties` | `enable`=`true`；`type`=`SPEC`；`ttl`=`120`（秒）；`length`=`4`；`width`=`120`；`height`=`36`；`font-name`=`Verdana`；`font-size`=`20`；`context-path`=`/v1/` |
| `plugin.captcha.external` | `ExternalCaptchaConfiguration` | 第三方打码服务账号（YesCaptcha / CaptchaRun） |
| `plugin.optional` | `OptionalProperties` | `enable`=`true`；关闭后 `/v1/option/**` 不注册 |
| `plugin.log` | `LogProperties` | `enable`=`true`；`open-interface-log`=`false`；`slow-request-threshold-ms`=`3000` |
| `plugin.thread` | `AsyncThreadPoolProperties` | `enable`=`false`；`core-pool-size`=`20`；`max-pool-size`=`200`；`queue-capacity`=`100000`；`keep-alive-seconds`=`10`；`await-termination-seconds`=`60`；`thread-name-prefix`=`async-thread-pool-%d` |
| `plugin.actuator` | `ActuatorProperties` | `enable`=`false`；`enable-auth`=`false`；`username`/`password`；`ip-whitelist`；`exclude-paths` |
| `plugin.jackson` | `JacksonProperties` | `enable`=`false`；`include-null`=`false` |
| `plugin.service-info` | `ServiceInfoProperties` | 启动横幅与 `/service-info` 的开关控制 |
| `plugin.security.fingerprint` | `RequestFingerprintAutoConfiguration` 内部类 | 请求指纹校验参数 |
| `plugin.ip` / `plugin.notice` / `plugin.nonce.sign` / `plugin.message` | `IpProperties` 等 | IP 解析库、通知、防重放签名、消息转换器 |

## 四、快速开始

**第一步**：引入依赖（见上）。模块通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 自动装配，无需 `@EnableXxx`。

**第二步**：按需打开开关。默认开启的有统一响应（`plugin.api.uniform`）与灰度（`plugin.api.gray.enable`），其余默认关闭。

**第三步**：在 Controller 上直接返回业务对象，由 `ApiUniformResponseBodyAdvice` 包装为 `ReturnResult`；返回文件流、`ResponseEntity`、`byte[]`、`StreamingResponseBody`、`DeferredResult` 等类型时不包装。

```yaml
plugin:
  api:
    uniform: true
    encode:
      enable: false
      codec-type: aes
      white-list:
        - /actuator/**
    gray:
      enable: true
      header-name: X-Gray-Hit
      global-users: [gray-user]
  captcha:
    enable: true
    width: 120
    height: 36
    length: 4
```

## 五、运行示例

### 5.1 调用示例

本模块无链式 API，以下为步骤式用法。

**统一响应的两种忽略方式**：

```java
@RestController
public class FileController {

    @GetMapping("/download")
    @ApiReturnFormatIgnore          // 方法级：返回原始数据
    public byte[] download() {
        return fileBytes;
    }
}

@RestController
@ApiReturnFormatIgnore              // 类级：整个 Controller 不包装
public class RawController { ... }
```

`@IgnoreReturnType`（来自 `com.chua.common.support.annotation.IgnoreReturnType`）同样被 `ApiUniformResponseBodyAdvice` 识别，两种注解可混用。

**接口版本**（需 `plugin.api.version.enable=true`，默认开启）：

```java
@RestController
@RequestMapping("/api/greet")
public class GreetController {

    @GetMapping
    @ApiVersion("1")
    public String v1() { return "hello-v1"; }

    @GetMapping
    @ApiVersion("2")
    public String v2() { return "hello-v2"; }
}
```

匹配方式：`?apiVersion=2`、请求头 `X-Api-Version: 2`、或 `?apiVersion=latest`（取最大版本）；未指定版本时命中最新版本。

**灰度与功能开关**：

```java
@GetMapping("/preview")
@ApiGray(users = {"gray-user"}, forceGray = true, notInGrayStatus = 403)
public String preview() { return "gray-preview-ok"; }

@GetMapping("/beta")
@ApiFeature(value = "beta-feature", defaultEnabled = false)
public String beta() { return "beta-ok"; }
```

**三个注解的属性速查**：

| `@ApiVersion` | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `value` | `String` | `"1"` | 版本号，语义化字符串（`"1"`、`"1.2"`、`"v2"`） |

| `@ApiGray` | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `value` | `String` | `""` | 灰度版本标识，命中写入 `X-Gray-Hit-Version` |
| `description` | `String` | `""` | 功能描述 |
| `percentage` | `int` | `0` | 灰度百分比（0-100） |
| `rule` | `String` | `""` | SpEL 规则 |
| `users` / `ips` / `roles` / `headers` | `String[]` | `{}` | 名单类命中条件 |
| `fallback` | `String` | `""` | 强制灰度未命中时的降级转发路径 |
| `notInGrayMessage` | `String` | `您暂未被邀请体验此功能` | 未命中文案 |
| `notInGrayStatus` | `int` | `200` | 未命中状态码（强制灰度时生效，建议 403） |
| `forceGray` | `boolean` | `false` | 未命中是否直接拒绝（默认软灰度放行） |
| `group` | `String` | `"default"` | 灰度分组 |

| `@ApiFeature` | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `value` | `String` | 必填 | 功能标识 |
| `description` | `String` | `""` | 功能描述 |
| `group` | `String` | `"default"` | 功能分组 |
| `defaultEnabled` | `boolean` | `true` | 默认是否开放（实验性功能建议 `false`） |
| `disabledMessage` | `String` | `此功能暂未开放` | 未开放文案 |
| `disabledStatus` | `int` | `503` | 未开放状态码 |

灰度开关 `plugin.api.gray.enable` 默认 `true`，拦截器由 `ApiFeatureConfiguration` 注册（`/**`，排除 `/error` 与 `/actuator/**`）；功能开关需 `plugin.api.feature.enable=true`。

**验证码**：

```java
// GET /v1/captcha/captcha  →  { "verifyCodeBase64": "data:image/png;base64,...", "verifyCodeKey": "ABCD" }
Captcha captcha = captchaProvider.producer();          // 使用默认 type/length
Captcha custom  = captchaProvider.producer(CaptchaTypeEnum.ARITHMETIC, 5);
```

**服务信息注册**（推荐 Lambda 形式的 `ServiceInfoContributor`）：

```java
@Configuration
public class MyConfiguration {

    @Bean
    public ServiceInfoContributor myContributor() {
        return registry -> {
            registry.registerEndpoint("文件系统", "分片上传", "/chunk/create,upload,complete", "分片上传");
            registry.registerFlag("oss", "存储", true, "阿里云 OSS 已启用");
        };
    }
}
```

也可直接调用单例 `ServiceInfoRegistry.getInstance().registerEndpoint(...)`。注册结果在启动横幅中渲染，并通过 `GET /service-info` 聚合输出（`endpoints` / `flags` 两个 Map）。

**方法追踪**：

```java
@Watch(value = "处理订单", timeoutMs = 2000, logArgs = true)
public Order processOrder(OrderRequest request) { ... }
```

### 5.2 SPI 扩展示例

模块通过 `META-INF/extensions/<接口全限定名>` 登记 SPI 实现，共三个扩展点：

| 扩展接口 | 已登记实现 |
|---|---|
| `com.chua.starter.common.support.media.MediaTypeHandler` | `XmlMediaTypeHandler`（另有 `@Extension("json")` / `@Extension("csv")` 注解实现） |
| `com.chua.starter.common.support.media.formatter.Formatter` | `DateFormatter`、`NoneFormatter` |
| `com.chua.common.support.lang.expression.parser.ExpressionParser` | `SpelExpressionParser` |

以新增 YAML 媒体处理器为例，三步：

```java
// 1. 实现接口，用 @Extension 声明名称
@Extension("yaml")
public class YamlMediaTypeHandler implements MediaTypeHandler {
    @Override
    public byte[] asByteArray(Object o) {
        return Yaml.toBytes(o);
    }
}
```

```text
# 2. 登记：在 src/main/resources/META-INF/extensions/ 下新建同名文件
#    com.chua.starter.common.support.media.MediaTypeHandler
com.example.YamlMediaTypeHandler
```

3. 生效：`ServiceProvider` 在首次按名取用时加载，`ServiceProvider.of(MediaTypeHandler.class).getExtension("yaml")` 即可命中；无需重启额外配置。

## 六、与其它 starter 的关系

- **上游**：依赖 `utils-support-common-starter`（`ReturnResult`、SPI、`@IgnoreReturnType`）、`utils-support-spring-starter`、`utils-support-httpclient-starter`。
- **下游（不反向依赖）**：pom 中已注释掉对 `spring-support-cache-starter` 的依赖，避免循环。缓存管理器 `systemCacheManager` / `systemCacheManager600` / `systemCacheManagerAlways` 由 `spring-support-redis-starter` 的 `CacheConfiguration` 提供；`spring-support-cache-starter` 提供 Caffeine 与多级缓存。
- **Swagger**：pom 不含 Swagger starter，`@Tag` / `@Operation` 只是 `provided` 依赖的注解，文档能力由 `spring-support-swagger-starter` 提供。
- **用户操作日志**：`@UserLogger` 切面（`UserLoggerPointcutAdvisor`）位于 `spring-support-oauth-client-starter`，本模块只提供事件模型 `UserLoggerInfo` / `SysLoggerInfo` 与 `LoggerIgnore`。
- **数据库/MyBatis**：本模块引入 `spring-boot-starter-jdbc` 仅用于 `DataAccessException` 等异常类型，数据源能力见 `spring-support-datasource-starter`。

## 七、已知限制

1. `ApiVersion` 依赖 `ApiConfiguration` 通过 `WebMvcRegistrations` 装配 `ApiVersionRequestMappingHandlerMapping`；若宿主自定义该扩展点并覆盖，版本路由会失效。
2. `ApiFeatureController` 的注册路径由 `plugin.api.feature.path` 决定，但功能开关整体受 `plugin.api.feature.enable`（默认 `false`）控制，未开启时注解拦截与查询接口都不生效。
3. 灰度依赖会话中的 `username` / `userId` 与 `roles` 属性；无会话（如纯 Token 无状态）时只能按 IP / 请求头规则命中。
4. 验证码依赖 `HttpSession`。分布式部署需自行配置 Session 共享（Spring Session），否则校验会跨节点失败。
5. `ServiceInfoRegistry` 是 JVM 级单例，`LinkedHashMap` 非线程安全，只应在容器初始化阶段注册；多 `ApplicationContext` 场景需调用 `reset()` 清理。
6. `MediaTypeHandler` 的 XML / CSV 实现依赖宿主提供的序列化库，缺失时仅该类型不可用。
7. 统一响应包装对所有 `@RestController` 生效，二进制流、SSE 等类型靠类型白名单放行；自定义返回类型若不在白名单内需加 `@ApiReturnFormatIgnore`。
8. `plugin.thread.enable` 默认 `false`，未开启时 `@Async` 使用 Spring 默认执行器。
9. 统一响应未生效时按序排查：Controller 是否为 `@RestController`、`plugin.api.uniform` 是否被显式关掉、返回类型是否命中忽略清单、方法/类上是否加了 `@ApiReturnFormatIgnore` 或 `@IgnoreReturnType`、所在包是否被 `plugin.api.ignore-format-packages` 排除。
