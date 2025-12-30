# Spring Support Swagger Starter

[![Maven Central](https://img.shields.io/maven-central/v/com.chua/spring-support-swagger-starter.svg)](https://search.maven.org/artifact/com.chua/spring-support-swagger-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 📖 模块简介

Spring Support Swagger Starter 是一个API文档自动生成模块，基于 Knife4j (Swagger) 提供交互式API文档界面，支持OpenAPI 3.0规范。

### ✨ 主要特性

- 📚 **自动文档生成** - 基于注解自动生成API文档
- 🎨 **美观界面** - Knife4j增强UI，比原生Swagger UI更美观
- 🔍 **在线测试** - 支持在线测试API接口
- 🔐 **文档认证** - 支持文档访问权限控制
- 📦 **分组管理** - 支持多分组API文档管理
- 🌍 **国际化支持** - 支持中英文文档

## 🚀 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-swagger-starter</artifactId>
    <version>4.0.0.33-SNAPSHOT</version>
</dependency>
```

### Gradle 依赖

```groovy
implementation 'com.chua:spring-support-swagger-starter:4.0.0.33-SNAPSHOT'
```

## ⚙️ 配置说明

### 基础配置

**配置前缀**: `plugin.knife4j`

| 参数名 | 类型 | 默认值 | 说明 |
|------|------|------|------|
| `enable` | Boolean | false | 是否启用Knife4j |
| `title` | String | - | 文档标题 |
| `description` | String | - | 文档描述 |
| `version` | String | 4.0.0 | API版本 |
| `contact.name` | String | - | 联系人姓名 |
| `contact.email` | String | - | 联系人邮箱 |
| `contact.url` | String | - | 联系人网站 |
| `base-package` | String | - | Controller包路径 |
| `production` | Boolean | false | 是否生产环境（生产环境将禁用） |

### 配置示例

```yaml
plugin:
  knife4j:
    enable: true
    title: My API Documentation
    description: RESTful API documentation for My Application
    version: 1.0.0
    contact:
      name: Development Team
      email: dev@example.com
      url: https://example.com
    base-package: com.yourcompany.controller
    production: false  # 生产环境设置为true将禁用文档
```

### Properties格式配置

```properties
plugin.knife4j.enable=true
plugin.knife4j.title=My API Documentation
plugin.knife4j.description=RESTful API documentation
plugin.knife4j.version=1.0.0
plugin.knife4j.contact.name=Development Team
plugin.knife4j.contact.email=dev@example.com
plugin.knife4j.contact.url=https://example.com
plugin.knife4j.base-package=com.yourcompany.controller
plugin.knife4j.production=false
```

## 📝 使用示例

### Controller注解示例

```java
@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理", description = "用户相关接口")
public class UserController {

    @Operation(summary = "获取用户信息", description = "根据ID获取用户详细信息")
    @Parameter(name = "id", description = "用户ID", required = true)
    @GetMapping("/{id}")
    public Result<User> getUserById(@PathVariable Long id) {
        return Result.success(userService.findById(id));
    }
    
    @Operation(summary = "创建用户")
    @Parameters({
        @Parameter(name = "username", description = "用户名", required = true),
        @Parameter(name = "email", description = "邮箱", required = true)
    })
    @PostMapping
    public Result<User> createUser(@RequestBody @Valid UserDTO userDTO) {
        return Result.success(userService.create(userDTO));
    }
    
    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public Result<User> updateUser(
            @PathVariable Long id,
            @RequestBody @Valid UserDTO userDTO) {
        return Result.success(userService.update(id, userDTO));
    }
    
    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }
    
    @Operation(summary = "用户列表", description = "分页查询用户列表")
    @GetMapping
    public Result<Page<User>> getUserList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(userService.findAll(page, size));
    }
}
```

### 实体类注解示例

```java
@Schema(description = "用户信息")
public class User {

    @Schema(description = "用户ID", example = "1")
    private Long id;
    
    @Schema(description = "用户名", required = true, example = "zhangsan")
    private String username;
    
    @Schema(description = "邮箱", required = true, example = "zhangsan@example.com")
    private String email;
    
    @Schema(description = "年龄", example = "25")
    private Integer age;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    // getters and setters
}

@Schema(description = "用户创建DTO")
public class UserDTO {

    @Schema(description = "用户名", required = true, minLength = 3, maxLength = 20)
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    @Schema(description = "邮箱", required = true)
    @Email(message = "邮箱格式不正确")
    private String email;
    
    @Schema(description = "年龄", minimum = "0", maximum = "150")
    @Min(value = 0, message = "年龄不能小于0")
    @Max(value = 150, message = "年龄不能大于150")
    private Integer age;
    
    // getters and setters
}
```

### 分组配置示例

```java
@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("用户模块")
                .pathsToMatch("/api/users/**")
                .build();
    }
    
    @Bean
    public GroupedOpenApi productApi() {
        return GroupedOpenApi.builder()
                .group("产品模块")
                .pathsToMatch("/api/products/**")
                .build();
    }
}
```

## 🌐 访问文档

启动应用后,访问以下地址查看API文档：

- **Knife4j文档**: http://localhost:8080/doc.html
- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **OpenAPI规范**: http://localhost:8080/v3/api-docs

## 🔒 生产环境安全

为了安全起见，建议在生产环境禁用API文档：

```yaml
plugin:
  knife4j:
    production: true  # 生产环境禁用文档
```

或者通过环境变量控制：

```yaml
plugin:
  knife4j:
    enable: ${SWAGGER_ENABLE:false}  # 默认禁用，需要时通过环境变量启用
```

## 🔗 相关链接

- [返回主文档](../README.md)
- [Knife4j官方文档](https://doc.xiaominfo.com/)
- [OpenAPI规范](https://swagger.io/specification/)
- [配置示例文件](../application-example.yml)

## 📄 许可证

本项目采用 [Apache License 2.0](../LICENSE) 许可证。
