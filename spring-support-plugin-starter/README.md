# Spring Support Plugin Starter

> 🚀 **完整实现 PF4J 所有功能的 Spring Boot Starter**

一个功能完整、开箱即用的插件系统 Spring Boot Starter，完全兼容 PF4J 标准，并提供更多增强功能。

## ✨ 特性

### PF4J 标准功能 (100% 实现)

- ✅ 插件加载/卸载/重载
- ✅ 插件生命周期管理
- ✅ 扩展点系统 (@Extension, @ExtensionPoint)
- ✅ 依赖管理和版本控制
- ✅ 类加载隔离
- ✅ 插件目录监听
- ✅ 热加载/热卸载

### 增强功能 (超越 PF4J)

- ✨ **ObjectContext 集成** - Bean 自动管理和依赖注入
- ✨ **SPI 扩展机制** - 高度可扩展的架构
- ✨ **Spring 注解支持** - 自动识别 @Service, @Component 等
- ✨ **Windows 完美支持** - 完美释放文件锁，支持热更新
- ✨ **零配置启动** - 添加依赖即可使用
- ✨ **运行模式** - 开发/生产模式自动优化

---

## 📦 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-plugin-starter</artifactId>
    <version>最新版本</version>
</dependency>
```

### 2. 配置（可选）

#### application.yml

```yaml
spring:
  plugin:
    enabled: true                    # 启用插件系统（默认：true）
    plugins-root: ./plugins         # 插件目录（默认：./plugins）
    auto-load: true                 # 自动加载（默认：true）
    auto-start: true                # 自动启动（默认：true）
    watch-enabled: true             # 热加载（默认：false）
    auto-reload: true               # 自动重载（默认：true）
    runtime-mode: development       # 运行模式（development/production）
    show-info: true                 # 显示插件信息（默认：true）
    show-details: false             # 显示详细信息（默认：false）
```

#### application.properties

```properties
spring.plugin.enabled=true
spring.plugin.plugins-root=./plugins
spring.plugin.watch-enabled=true
spring.plugin.runtime-mode=development
```

### 3. 启动应用

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

**就这么简单！** 插件系统会自动：
1. 创建插件目录
2. 扫描并加载所有插件
3. 启动所有插件
4. 注册插件 Bean 到容器

---

## 📋 配置属性详解

| 属性 | 类型 | 默认值 | 说明 |
|-----|------|--------|------|
| `spring.plugin.enabled` | boolean | true | 是否启用插件系统 |
| `spring.plugin.plugins-root` | String | ./plugins | 插件根目录路径 |
| `spring.plugin.auto-load` | boolean | true | 是否自动加载插件 |
| `spring.plugin.auto-start` | boolean | true | 是否自动启动插件 |
| `spring.plugin.watch-enabled` | boolean | false | 是否启用目录监听（热加载） |
| `spring.plugin.auto-reload` | boolean | true | 文件修改时是否自动重载 |
| `spring.plugin.runtime-mode` | enum | DEVELOPMENT | 运行模式（DEVELOPMENT/PRODUCTION） |
| `spring.plugin.show-info` | boolean | true | 是否显示插件信息 |
| `spring.plugin.show-details` | boolean | false | 是否显示详细信息 |
| `spring.plugin.strict-mode` | boolean | false | 是否严格模式 |
| `spring.plugin.resolve-dependencies` | boolean | true | 是否解析依赖 |

---

## 🎯 运行模式

### 开发模式 (DEVELOPMENT)

适合本地开发和调试：

```yaml
spring:
  plugin:
    runtime-mode: development
    watch-enabled: true      # 自动启用
    show-info: true
    show-details: true       # 显示详细信息
```

**特性：**
- ✅ 支持热加载
- ✅ 详细的日志输出
- ✅ 完整的插件信息

### 生产模式 (PRODUCTION)

适合生产环境：

```yaml
spring:
  plugin:
    runtime-mode: production
    watch-enabled: false     # 自动禁用
    show-info: false
    show-details: false
```

**特性：**
- ✅ 禁用热加载（性能优化）
- ✅ 简洁的日志输出
- ✅ 更高的稳定性

---

## 🔌 创建插件

### 1. 创建插件项目

```xml
<project>
    <groupId>com.example</groupId>
    <artifactId>my-plugin</artifactId>
    <version>1.0.0</version>
    
    <dependencies>
        <dependency>
            <groupId>com.chua</groupId>
            <artifactId>utils-support-common-starter</artifactId>
            <version>最新版本</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

### 2. 创建插件类

```java
package com.example.myplugin;

import com.chua.common.support.objects.plugin.api.Plugin;

public class MyPlugin extends Plugin {
    
    @Override
    public void start() {
        System.out.println("MyPlugin started!");
    }
    
    @Override
    public void stop() {
        System.out.println("MyPlugin stopped!");
    }
    
    @Override
    public void delete() {
        System.out.println("MyPlugin deleted!");
    }
}
```

### 3. 创建插件描述符

在 `src/main/resources/plugin.properties`：

```properties
plugin.id=my-plugin
plugin.name=My Plugin
plugin.version=1.0.0
plugin.description=My awesome plugin
plugin.class=com.example.myplugin.MyPlugin
plugin.provider=Your Name
plugin.license=Apache 2.0
```

### 4. 创建服务类

#### 使用 @Extension 注解

```java
@Extension
public class MyService {
    public String hello() {
        return "Hello from plugin!";
    }
}
```

#### 使用 Spring 注解（自动识别）

```java
@Service
public class MySpringService {
    public String sayHi() {
        return "Hi from Spring plugin!";
    }
}
```

### 5. 打包

```bash
mvn clean package
```

生成的 JAR 文件：`target/my-plugin-1.0.0.jar`

---

## 💻 使用插件

### 注入插件管理器

```java
@RestController
@RequestMapping("/plugin")
public class PluginController {
    
    @Autowired
    private PluginManager pluginManager;
    
    @GetMapping("/list")
    public List<String> listPlugins() {
        return pluginManager.getPlugins().stream()
            .map(PluginWrapper::getPluginId)
            .collect(Collectors.toList());
    }
    
    @GetMapping("/load")
    public String loadPlugin(@RequestParam String path) throws Exception {
        return pluginManager.loadPlugin(new File(path));
    }
    
    @PostMapping("/unload/{id}")
    public boolean unloadPlugin(@PathVariable String id) throws Exception {
        return pluginManager.unloadPlugin(id);
    }
    
    @PostMapping("/reload/{id}")
    public void reloadPlugin(@PathVariable String id) throws Exception {
        pluginManager.reloadPlugin(id);
    }
}
```

### 获取插件扩展点

```java
@Service
public class PluginService {
    
    @Autowired
    private PluginManager pluginManager;
    
    public void useExtensions() {
        // 获取所有实现了某接口的扩展
        List<MyService> services = 
            pluginManager.getExtensions(MyService.class);
        
        for (MyService service : services) {
            System.out.println(service.hello());
        }
    }
}
```

---

## 🔥 热加载

### 启用热加载

```yaml
spring:
  plugin:
    watch-enabled: true
    auto-reload: true
```

### 行为说明

#### 新增插件 (CREATE)
```
./plugins/new-plugin-1.0.0.jar ← 添加文件
↓
自动加载并启动插件
```

#### 修改插件 (MODIFY)
```
./plugins/my-plugin-1.0.0.jar ← 更新文件
↓
自动卸载 → 加载 → 启动
```

#### 删除插件 (DELETE)
```
./plugins/old-plugin-1.0.0.jar ← 删除文件
↓
自动卸载插件
```

---

## 📊 PF4J 功能对比

| 功能 | PF4J | 本 Starter | 说明 |
|------|------|-----------|------|
| 插件管理 | ✅ | ✅ | 完全兼容 |
| 扩展点 | ✅ | ✅ | 完全兼容 |
| 热加载 | ✅ | ✅ | 完全兼容 |
| Bean管理 | ❌ | ✅ | **增强功能** |
| Spring集成 | ⚠️ | ✅ | **自动识别** |
| SPI扩展 | ❌ | ✅ | **独有功能** |
| Windows支持 | ⚠️ | ✅ | **完美支持** |

**结论：完全兼容 PF4J，并提供更多增强功能！**

---

## 🎯 最佳实践

### 开发环境

```yaml
spring:
  plugin:
    runtime-mode: development
    watch-enabled: true
    show-details: true
```

### 生产环境

```yaml
spring:
  plugin:
    runtime-mode: production
    plugins-root: /opt/app/plugins  # 绝对路径
    watch-enabled: false            # 禁用热加载
    show-info: false
```

### 插件命名

```
<plugin-id>-<version>.jar
示例：my-plugin-1.0.0.jar
```

---

## 🔍 故障排查

### 插件加载失败

**检查：**
1. plugin.properties 是否存在
2. plugin.class 是否正确
3. JAR 文件是否完整

### 热加载不工作

**检查：**
1. watch-enabled 是否为 true
2. 插件目录路径是否正确
3. Windows：文件是否被锁定

---

## 📚 相关文档

- [完整功能对比](../../PF4J_FEATURE_COMPARISON.md)
- [使用指南](../../SPRING_BOOT_PLUGIN_GUIDE.md)
- [卸载机制](../../PLUGIN_UNLOAD_GUIDE.md)

---

## 💡 示例项目

完整的示例项目请参考：`examples/plugin-demo`

---

## 🎉 总结

这是一个**生产就绪、功能完整、文档齐全**的企业级插件系统 Spring Boot Starter！

- ✅ **完全兼容 PF4J**
- ✅ **零配置启动**
- ✅ **开箱即用**
- ✅ **高度可扩展**
- ✅ **完美的 Spring 集成**

**立即开始使用吧！** 🚀
