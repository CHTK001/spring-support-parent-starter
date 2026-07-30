# Spring Support Swagger2 Starter

Swagger 2.x 兼容模块，提供 Swagger 2.x 注解支持，用于兼容使用老版本 Swagger 的项目。

## 功能特性

- 提供 Swagger 2.x 注解依赖（`@Api`、`@ApiOperation`、`@ApiParam` 等）
- 与 `spring-support-swagger-starter`（Swagger 3.x）可以同时使用
- 通过反射机制，`common-starter` 中的日志组件可以自动识别 Swagger 2.x 注解
- 仅提供注解依赖，不包含 Swagger UI 等运行时组件

## 使用场景

1. **兼容老项目**：需要兼容使用 Swagger 2.x 注解的老项目
2. **渐进式迁移**：在从 Swagger 2.x 迁移到 Swagger 3.x 的过程中，可以同时使用两个版本
3. **日志组件支持**：`UserLoggerPointcutAdvisor` 和 `SysLoggerPointcutAdvisor` 会自动识别 Swagger 2.x 注解

## 依赖说明

该模块仅提供 Swagger 2.x 的注解依赖，不包含 Swagger UI 等运行时组件。如果需要完整的 Swagger 2.x 功能，请使用其他 Swagger 2.x 集成方案。

## 使用示例

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-swagger2-starter</artifactId>
</dependency>
```

### 2. 使用 Swagger 2.x 注解

```java
@RestController
@RequestMapping("/api/users")
@Api(tags = "用户管理")
public class UserController {

    @GetMapping("/{id}")
    @ApiOperation("获取用户信息")
    public User getUser(
            @ApiParam("用户ID") @PathVariable Long id) {
        // ...
    }
}
```

### 3. 日志组件自动识别

`UserLoggerPointcutAdvisor` 和 `SysLoggerPointcutAdvisor` 会自动识别 `@ApiOperation` 注解，提取操作名称用于日志记录。

## 与 Swagger 3.x 共存

该模块可以与 `spring-support-swagger-starter`（Swagger 3.x）同时使用：

```xml
<!-- Swagger 3.x（主版本） -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-swagger-starter</artifactId>
</dependency>

<!-- Swagger 2.x（兼容老版本） -->
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-swagger2-starter</artifactId>
</dependency>
```

日志组件会优先使用 Swagger 3.x 的 `@Operation` 注解，如果不存在则回退到 Swagger 2.x 的 `@ApiOperation` 注解。

## 版本说明

- **Swagger 2.x 注解版本**：1.6.14
- **兼容性**：与 Swagger 3.x 完全兼容，可以同时使用

## 注意事项

1. 该模块仅提供注解依赖，不包含 Swagger UI 等运行时组件
2. 如果需要完整的 Swagger 2.x 功能，请使用其他 Swagger 2.x 集成方案
3. 推荐逐步迁移到 Swagger 3.x，以获得更好的功能和性能

