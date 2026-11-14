# spring-support-swagger-starter

基于 springdoc + Knife4j 的 OpenAPI 3 接口文档 starter：自动聚合 `@RestController` 形成交互式文档，并提供离线导出（HTML/Markdown/Word）。未显式关闭时文档默认开启（自动配置条件 `matchIfMissing=true`），显式配置 `plugin.swagger.enable=false` 可整体关闭。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| 文档聚合与 Knife4j UI | `Knife4jConfiguration` | `/doc.html`、`/swagger-ui.html`、`/v3/api-docs` |
| springdoc 配置兜底 | `SpringDocConfigPropertiesConfiguration` | `SpringDocConfigProperties` Bean |
| 静态资源与页面重定向 | `Knife4jWebMvcConfigurer` | `/doc.html`、`/doc-v2.html`、`/doc`、`/doc-v2` |
| 环境预配置（knife4j/springdoc 桥接） | `SwaggerEnvironmentPostProcessor` | `spring.factories`（EnvironmentPostProcessor） |
| 分组 → `springdoc.group-configs` / `knife4j.documents` | `SwaggerEnvironmentPostProcessor` | `plugin.swagger.knife4j[]` |
| 枚举属性文档增强 | `Knife4jEnumModelPropertyPlugin`（`PropertyCustomizer`） | 实现 `SwaggerEnum` 的枚举属性 |
| 在线导出 HTTP 端点 | `SwaggerExportController` | `/chua/swagger/export`、`/chua/swagger/export/preview` |
| 启动期离线导出 | `SwaggerExportRunner` | `plugin.swagger.export.enabled=true` |
| 文档渲染导出（HTML/Markdown/Word） | `SwaggerOpenApiExporter` + `OpenApiConverter` | `OpenApiDocumentProvider` SPI |
| 服务信息登记 | `Knife4jConfiguration#knife4jServiceInfoContributor` | 控制台横幅 |
| springdoc 兼容补丁 | `org.springdoc.core.service.GenericResponseService` | classpath 同包同类覆盖 |

实际生效的能力：

- 未配置时文档默认开启，`/doc.html`（Knife4j UI）、`/swagger-ui.html`、`/v3/api-docs` 可用；
- 环境后处理器把 `plugin.swagger.*` 桥接成 `knife4j.*` 与 `springdoc.*` 两组属性（最低优先级，应用配置可覆盖）；
- 配置了 `plugin.swagger.knife4j[]` 分组时，自动注册对应 `springdoc.group-configs` 与 `knife4j.documents`；
- 文档默认启用 Basic 认证（`knife4j.basic.enable=true`），未配置账号密码时是 `root / root123`（见「七、已知限制」）；
- 实现了 `SwaggerEnum` 接口的枚举，其模型属性会展示 `code:name` 说明与枚举值列表；
- `plugin.swagger.export.enabled=true`（默认）时，启动期导出 `./swagger-export.html`，并提供在线下载/预览端点。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-swagger-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 |
|:-----|:-----|
| `com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter`（4.5.0） | Knife4j UI 与 springdoc 集成 |
| `io.swagger.core.v3:swagger-annotations`（2.2.55，父 pom 依赖管理） | OpenAPI 3 注解 |
| `spring-boot-actuator` / `spring-boot-actuator-autoconfigure` | `WebMvcEndpointHandlerMapping` 兼容 Bean 用到的端点类型 |
| `spring-boot-webmvc` | Boot 4.x 中 actuator 端点映射拆分至此模块 |
| `spring-support-common-starter`（provided） | `ServiceInfoContributor` / `ServiceInfoRegistry`（横幅登记） |
| `com.chua.common.support.lang.document`（utils 层，传递引入） | `OpenApiDocumentProvider` SPI 与文档数据模型 |

## 三、配置项

配置前缀：**`plugin.swagger`**（`Knife4jProperties`）与 **`plugin.swagger.export`**（`SwaggerExportProperties`）。
注意：老版本 README 写的 `plugin.knife4j` 前缀在本模块源码中不存在，实际前缀均为 `plugin.swagger`。

### 3.1 基础（`plugin.swagger`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.swagger.enable` | `false`（字段默认值） | 总开关。但自动配置条件 `matchIfMissing=true`，**未配置时文档默认开启**；显式 `false` 才关闭 |
| `plugin.swagger.default-header` | 无 | Map 形式的默认请求头，仅供 `ParameterFilterOperationCustomizer` 消费，**该类未装配，实际不生效** |
| `plugin.swagger.username` | `""`（空串） | 文档 Basic 认证账号；经桥接后缺省取 `root` |
| `plugin.swagger.password` | `""`（空串） | 文档 Basic 认证密码；经桥接后缺省取 `root123` |
| `plugin.swagger.log` | `false` | 是否开启 Swagger 注解记录操作日志；**仅声明，运行期未读取** |
| `plugin.swagger.description` | `无` | 文档描述，注册 `knife4j-OpenAPI` Bean 时写入 Info |
| `plugin.swagger.version` | `1.0` | 文档版本，注册 `knife4j-OpenAPI` Bean 时写入 Info |
| `plugin.swagger.terms-of-service` | 内置长条款文本 | 服务条款，注册 `knife4j-OpenAPI` Bean 时写入 Info |

### 3.2 分组（`plugin.swagger.knife4j[]`，内部类 `Knife4jProperties.Knife4j`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.swagger.knife4j[].group` | 无 | 分组编码；为空时取 `group-name`（用于 `knife4j.documents[i].group/name`） |
| `plugin.swagger.knife4j[].group-name` | 无 | 分组名，用于 `springdoc.group-configs[i].display-name` |
| `plugin.swagger.knife4j[].paths-to-match` | 无（桥接时空值缺省 `/**`） | 该分组匹配的路径 |
| `plugin.swagger.knife4j[].base-package` | 无 | 该分组扫描的包，映射为 `packages-to-scan` |
| `plugin.swagger.knife4j[].title` | `""` | 分组标题 |
| `plugin.swagger.knife4j[].description` | `""` | 分组描述 |
| `plugin.swagger.knife4j[].version` | `v1.0.0` | 分组版本 |

### 3.3 导出（`plugin.swagger.export`，`SwaggerExportProperties`）

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.swagger.export.enabled` | `true` | 是否启用导出（`SwaggerExportRunner` 与 `SwaggerExportController` 的条件，`matchIfMissing=true`） |
| `plugin.swagger.export.output` | `./swagger-export.html` | 输出文件路径；后缀（`.html/.md/.docx`）决定导出类型 |
| `plugin.swagger.export.title` | `Open API 接口文档` | 顶部标题，仅在原 OpenAPI 无标题时回填 |
| `plugin.swagger.export.version` | `1.0.0` | 版本徽标，仅在原 OpenAPI 无版本时回填 |
| `plugin.swagger.export.download-after-start` | `true` | 启动后是否立即导出（Runner 检查该开关） |
| `plugin.swagger.export.include-request-sample` | `true` | **仅声明，运行期未读取** |
| `plugin.swagger.export.path` | `/chua/swagger/export` | HTTP 端点基础路径（`@RequestMapping` 占位符，不在 `SwaggerExportProperties` 内） |

## 四、快速开始

**第一步：引入依赖**（见上，依赖本身不额外配置即可用）。

**第二步：配置 yaml**（按需；不配置也会默认开启）：

```yaml
plugin:
  swagger:
    enable: true        # 显式关闭用 false
    username: admin
    password: admin@2026
    description: 业务接口文档
    version: 1.0.0
    knife4j:
      - group: user
        group-name: 用户模块
        title: 用户服务
        base-package:
          - com.example.user.controller
        paths-to-match:
          - /user/**
    export:
      enabled: true
      output: ./docs/api.html
      download-after-start: true
```

**第三步：启动后验证**

```bash
# Knife4j 增强 UI（默认 Basic 认证：root / root123，或用上面配置的账号）
curl -u admin:admin@2026 http://localhost:8080/doc.html
# 标准 Swagger UI / OpenAPI JSON
curl http://localhost:8080/swagger-ui.html
curl http://localhost:8080/v3/api-docs
# 离线导出与预览
curl -u admin:admin@2026 'http://localhost:8080/chua/swagger/export?format=markdown' -o api.md
curl -u admin:admin@2026 http://localhost:8080/chua/swagger/export/preview
```

启动成功后控制台横幅会出现 `knife4j · 文档` 开关标记（由 `knife4jServiceInfoContributor` 登记，`ServiceInfoBannerListener` 渲染）。

## 五、运行示例

### 5.1 调用示例

**Controller 注解（OpenAPI 3 / Swagger 3 风格）**

```java
@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理")
public class UserController {

    @Operation(summary = "获取用户信息")
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return new User(id);
    }
}
```

**实现 `SwaggerEnum` 接口展示枚举说明**

```java
public enum UserStatus implements SwaggerEnum {
    ENABLED(1, "启用"),
    DISABLED(0, "禁用");

    private final int code;
    private final String name;

    UserStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }

    @Override
    public Object getCode() {
        return code;
    }

    @Override
    public String getName() {
        return name;
    }
}
```

`Knife4jEnumModelPropertyPlugin` 会把这类枚举属性的文档描述渲染为 `1:启用, 0:禁用`。

**隐藏字段**（`@HiddenParam`，同时被 `SwaggerEnum` 体系外的模型忽略逻辑使用）

```java
public class QueryDTO {
    @HiddenParam
    private String internalToken;   // 不入文档
    private String keyword;
}
```

### 5.2 SPI 扩展示例

本模块的核心扩展点是**导出渲染链路**：`SwaggerOpenApiExporter` 与 `SwaggerExportController`
不耦合具体渲染实现，统一委托给 utils 层的 `com.chua.common.support.lang.document.OpenApiDocumentProvider`
SPI（`create(type)` 按 `html` / `markdown` / `word` 查找实现）。要自定义导出主题或排版，
可在 SPI 层面提供/替换对应 type 的 `OpenApiDocumentProvider` 实现，代码无需改动。

除导出链路外**不提供其它 SPI 扩展点**（无 `META-INF/extensions`）。要改变文档聚合行为，
可替换 `Knife4jConfiguration` 或自行注入 springdoc 的 customizer/`OpenAPI` Bean。
注意：`customize` 包下的 customizer 与 `resolver`/`converter` 包下的解析器
（`CustomOperationCustomizer`、`CustomKnife4jOpenApiCustomizer`、`ParameterFilterOperationCustomizer`、
`CustomModelResolver`、`InheritedPropertyResolver`）源码存在但**未装配**（见「七、已知限制」），
不要依赖它们生效。

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
|:-----|:-----|:-----|
| `spring-support-common-starter` | 本模块依赖（provided） | `ServiceInfoContributor` 横幅登记；日志切面 `AbstractLoggerPointcutAdvisor` 负责识别 Swagger 注解 |
| `spring-support-swagger2-starter` | 姊妹模块，可共存 | 提供 `io.swagger.annotations`（Swagger 2.x 注解）兼容老项目；本模块用 `io.swagger.v3`（3.x）注解，二者注解包不同不冲突 |
| `spring-support-oauth-client-starter` | 反向可选协作 | 本模块的 `CustomModelResolver`/`ParameterFilterOperationCustomizer` 反射识别其 `UserValue` 注解（但两类均未装配）；common 的操作日志切面优先识别 3.x `Operation`、回退 2.x `ApiOperation` |
| `spring-boot-webmvc` / actuator | 本模块依赖 | `webEndpointServletHandlerMapping` 兼容 Bean（解决 springdoc 与 Boot 的映射兼容问题） |
| `utils` 层（`com.chua.common.support.lang.document`） | 本模块间接使用 | 导出链路的 `OpenApiDocumentData` / `OpenApiDocumentProvider` SPI |

注意区分：

- **本模块 = 文档生成与展示**（Knife4j UI + OpenAPI 3）；
- **`spring-support-swagger2-starter` = 仅 2.x 注解兼容壳**（无 UI、无自动配置）；
- `plugin.swagger.enable` 是总开关，`plugin.swagger.export.enabled` 只控制导出，两者独立。

## 七、已知限制

- **`enable` 字段默认值与条件语义相反**：`plugin.swagger.enable` 字段默认 `false`，但自动配置
  条件 `matchIfMissing=true`——未配置时文档**默认开启**，必须显式 `plugin.swagger.enable=false` 才能关闭。
- **Basic 认证默认强制开启**：`SwaggerEnvironmentPostProcessor` 固定写入 `knife4j.basic.enable=true`，
  且账号密码占位符缺省为 `root / root123`。未配置 `plugin.swagger.username/password` 时，
  `/doc.html` 等文档页面带 Basic 认证且为弱口令，生产环境必须显式配置。
- **一批定制类未装配**：`CustomOperationCustomizer`、`CustomKnife4jOpenApiCustomizer`、
  `ParameterFilterOperationCustomizer`（三个 `GlobalOperation*Customizer`，其中
  `CustomOperationCustomizer`/`ParameterFilterOperationCustomizer` 会消费 `default-header`、过滤
  `@HiddenParam`/`UserValue` 参数）、以及 `CustomModelResolver`、`InheritedPropertyResolver`
  均定义了代码，但没有任何 `@Bean`/`@Component`/`imports`/`META-INF/services` 注册入口，运行期全部不生效。
  相应能力（默认请求头、隐藏参数过滤、继承属性合并、`@UserValue` 隐藏）当前不可用。
- **`log` / `include-request-sample` 字段仅声明**：`plugin.swagger.log` 与
  `plugin.swagger.export.include-request-sample` 在运行期没有任何代码读取。
- **导出 Runner / Controller 依赖组件扫描**：`SwaggerExportRunner`（`@Component`）与
  `SwaggerExportController`（`@RestController`）不在 `AutoConfiguration.imports` 中，是否注册取决于
  应用组件扫描是否覆盖 `com.chua.starter.swagger.support.export` 包；扫描不到时启动导出与在线导出端点不生效。
- **springdoc 补丁需随版本维护**：`org.springdoc.core.service.GenericResponseService` 是 springdoc 2.3.0
  的同包同类源码补丁（解决 knife4j 4.5 依赖的 springdoc 2.3 与 Boot 兼容问题），升级 knife4j/springdoc
  后需要同步该补丁，否则可能因类签名不一致失效。
- **导出标题/版本仅在缺失时回填**：`export.title` / `export.version` 只在原 OpenAPI 信息为空时生效，
  配置了 `description`/`version` 的 OpenAPI 不会被覆盖。
- **`knife4j-OpenAPI` Bean 由代码注册**：`Knife4jConfiguration#postProcessBeanDefinitionRegistry`
  以 `registerBeanDefinition("knife4j-OpenAPI", ...)` 方式注册，`description`/`version`/`terms-of-service`
  仅在该 Bean 上体现，不参与 springdoc 的默认分组信息聚合。
- **`Knife4jRunner` 已废弃**：`@Deprecated`，`run()` 为空方法，启动横幅由
  `ServiceInfoBannerListener` 统一渲染，不要依赖它打印日志。