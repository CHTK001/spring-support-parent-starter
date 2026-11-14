# spring-support-swagger2-starter

Swagger 2.x 兼容壳 starter：只提供 `io.swagger.annotations`（`@Api`、`@ApiOperation`、`@ApiParam`
等注解依赖），供仍在用老版 Swagger 注解的项目使用。默认开、无运行时组件，不含自动配置类，
也不会启动任何 Bean 或占用端口。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| Swagger 2.x 注解依赖（provided） | `io.swagger.annotations`（依赖内） | `@Api`、`@ApiOperation`、`@ApiParam` 等 |
| 操作日志注解识别（配合方） | `AbstractLoggerPointcutAdvisor`（spring-support-common-starter） | `ApiOperation.value` 作日志操作名 |
| 自动配置 | — | **无**（`AutoConfiguration.imports` 仅注释说明） |

实际生效的能力：

- 引入后 `io.swagger.annotations` 包下注解可直接用于老项目代码；
- 与 `spring-support-swagger-starter`（3.x 注解）**可同时引入**：二者注解包名不同（`io.swagger` vs `io.swagger.v3`），无类冲突；
- common-starter 的操作日志切面按「注解 name > Swagger 3.x `Operation.summary` > Swagger 2.x `ApiOperation.value`」的顺序回退识别，
  因此本模块可让老代码的操作日志也拿到接口描述；
- 不注册任何自动配置类、不装配 Bean、不暴露 HTTP 端点，也没有 web 资源。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-swagger2-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 |
|:-----|:-----|
| `io.swagger:swagger-annotations`（1.6.16，父 pom 依赖管理，provided） | Swagger 2.x 注解，仅编译期需要 |
| `spring-boot-autoconfigure` | 本模块自身的自动配置声明（实际为空） |
| `com.chua:utils-support-common-starter` | 工具层依赖（版本由父 pom 的 `utils.version` 管理） |
| `spring-support-common-starter` | **无直接依赖**；日志切面识别 2.x 注解靠的是类加载器反射，编译期不需要本模块 |

## 三、配置项

**本模块无任何 `@ConfigurationProperties`，也没有自动配置类，因此没有任何配置项。**

不要为它配置 `plugin.swagger.*`（那是 `spring-support-swagger-starter` 的前缀）——
本模块既不读取配置，也不参与文档装配。

## 四、快速开始

**第一步：引入依赖**（见上）。

**第二步：在老代码上使用 Swagger 2.x 注解**（无需任何配置）：

```java
@RestController
@RequestMapping("/api/users")
@Api(tags = "用户管理")
public class UserController {

    @GetMapping("/{id}")
    @ApiOperation("获取用户信息")
    public User getUser(
            @ApiParam("用户ID") @PathVariable Long id) {
        return userService.findById(id);
    }
}
```

**第三步：与 3.x 文档模块共存**（可选）

若项目同时引入 `spring-support-swagger-starter`，则文档页面照常由 3.x 模块提供
（注解与配置均用 3.x 体系），本模块只保证老代码的 2.x 注解不报缺类、
且操作日志切面能回退识别 `ApiOperation.value`。

## 五、运行示例

### 5.1 调用示例

本模块**无服务端代码、无 Bean、无链式 API**，调用方式就是在业务代码里使用 2.x 注解。
日志侧的使用方式（配合方，common 模块行为）：

```java
// 假设方法上有 Swagger 2.x 注解
@ApiOperation("删除用户")
@DeleteMapping("/{id}")
public void delete(@PathVariable Long id) { ... }

// 操作日志切面识别顺序：注解 name -> Operation.summary (3.x) -> ApiOperation.value (2.x)
// 上面方法未显式给 name 时，操作日志的"操作名称"会取到 "删除用户"
```

### 5.2 SPI 扩展示例

**本模块不提供 SPI 扩展点**：没有 `META-INF/services`、没有扩展接口、没有可替换的 Bean。
注解识别逻辑位于 `spring-support-common-starter` 的 `AbstractLoggerPointcutAdvisor`（`UserLoggerPointcutAdvisor`
实现在 `spring-support-oauth-client-starter`），如需改变日志识别规则只能替换/覆写对应切面 Bean。

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
|:-----|:-----|:-----|
| `spring-support-swagger-starter` | 姊妹模块，可共存 | 3.x 文档模块（springdoc + Knife4j + 导出）；与 2.x 注解包名不同，可同时引入 |
| `spring-support-common-starter` | 间接协作，非直接依赖 | `AbstractLoggerPointcutAdvisor` 反射识别 `io.swagger.annotations.ApiOperation` |
| `spring-support-oauth-client-starter` | 间接协作 | `UserLoggerPointcutAdvisor` 等日志切面组件所在模块，共用 common 的识别逻辑 |
| `utils-support-common-starter` | 本模块依赖 | 工具层（注解壳本身不直接使用） |

注意区分：

- **本模块 = 2.x 注解壳**：只给老代码补注解类，无 UI、无文档生成；
- **`spring-support-swagger-starter` = 3.x 文档**：负责 UI、分组、导出等实际能力；
- 两者前缀都是各自独立：文档配置（`plugin.swagger.*`）只属于 3.x 模块，与本模块无关。

## 七、已知限制

- **不提供任何运行时能力**：`AutoConfiguration.imports` 只有注释说明，无自动配置类；
  `target` 产物里唯一非元数据文件就是这份 imports（内容为注释）。引入后不会有文档页面、
  不会注册 Bean、不会有路由，期望"Swagger 2 增强 UI"的用法不成立。
- **仅注解依赖**：`swagger-annotations` 是 provided 依赖，且不含 Swagger 2.x 的
  model/UI/springfox 等任何运行时组件；`@EnableSwagger2` 等注解不存在。
- **不生成 3.x 文档模型**：老项目用 2.x 注解写的接口，不会自动出现在
  `spring-support-swagger-starter` 的文档里（3.x 模块解析的是 `io.swagger.v3` 注解）。
- **版本受父 pom 约束**：`1.6.16` 由父 pom `${swagger.annotations.v2.version}` 管理，
  本模块未单独钉版本，升级需在父 pom 统一调整。