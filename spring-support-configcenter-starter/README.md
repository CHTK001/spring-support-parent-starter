# spring-support-configcenter-starter

## 📖 模块简介

**配置中心模块** - 提供分布式配置管理功能，支持动态配置刷新、配置版本管理、多环境配置等特性。

## ✨ 核心功能

### 🔧 配置管理

- ✅ 集中式配置管理
- ✅ 多环境配置支持
- ✅ 配置动态刷新
- ✅ 配置版本管理
- ✅ 配置加密存储

### 🔄 配置同步

- ✅ 配置变更实时推送
- ✅ 配置缓存机制
- ✅ 配置回滚支持

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-configcenter-starter</artifactId>
    <version>4.0.0.32</version>
</dependency>
```

### 2. 配置开关

**配置文件**：`application.yml`

```yaml
plugin:
  config-center:
    # 是否启用配置中心
    # 默认: false
    # 说明: 设置为true时才会启用配置中心功能
    enable: true

    # 配置中心地址
    server-url: http://localhost:8888

    # 应用名称
    application: ${spring.application.name}

    # 环境
    profile: ${spring.profiles.active}

    # 命名空间
    namespace: default
```

### 3. 使用配置

```java
@Value("${custom.config.key}")
private String configValue;

// 或使用 @ConfigurationProperties
@ConfigurationProperties(prefix = "custom.config")
@Data
public class CustomConfig {
    private String key;
}
```

## ⚙️ 配置说明

### 完整配置示例

```yaml
plugin:
  config-center:
    # 功能开关
    enable: true

    # 配置中心服务地址
    server-url: http://config-server:8888

    # 应用名称（用于区分不同应用的配置）
    application: my-app

    # 环境（dev/test/prod）
    profile: dev

    # 命名空间（用于配置隔离）
    namespace: default

    # 配置刷新间隔（秒）
    refresh-interval: 60

    # 是否启用配置加密
    enable-encryption: true

    # 加密密钥
    encryption-key: your-secret-key
```

## 💡 使用示例

### 动态刷新配置

```java
@RefreshScope
@RestController
public class ConfigController {

    @Value("${custom.message}")
    private String message;

    @GetMapping("/message")
    public String getMessage() {
        return message;  // 配置变更后自动刷新
    }
}
```

### 监听配置变更

```java
@Component
public class ConfigChangeListener {

    @EventListener
    public void onConfigChange(ConfigChangeEvent event) {
        log.info("配置变更: {} -> {}",
            event.getOldValue(),
            event.getNewValue());
    }
}
```

## 🎯 设计原则

### 1. 配置隔离

- ✅ 按应用隔离配置
- ✅ 按环境隔离配置
- ✅ 按命名空间隔离配置

### 2. 高可用

- ✅ 本地配置缓存
- ✅ 配置中心故障降级
- ✅ 配置变更通知机制

### 3. 安全性

- ✅ 配置加密存储
- ✅ 访问权限控制
- ✅ 配置变更审计

## 🏗️ 系统架构流程图

### 1. 整体系统架构

```mermaid
%%{init: {'theme':'base', 'themeVariables': { 'primaryColor':'#fff'}}}%%
flowchart TB
    subgraph Application["应用层 Application Layer"]
        SpringApp["Spring Boot应用<br/>SpringBootApplication"]
        UserCode["用户代码<br/>使用@ConfigValue注解"]
        ConfigController["ConfigController<br/>配置管理接口"]
    end
    
    subgraph Config["配置层 Configuration Layer"]
        ConfigCenterProperties["ConfigCenterProperties<br/>配置属性读取<br/>application.yml"]
        ConfigProps["配置属性<br/>enable protocol<br/>address hotReload等"]
    end
    
    subgraph PostProcessor["环境后置处理层 EnvironmentPostProcessor Layer"]
        ConfigCenterConfigurationEnvironmentPostProcessor["ConfigCenterConfigurationEnvironmentPostProcessor<br/>环境后置处理器<br/>在环境准备阶段加载配置"]
        LoadConfig["加载配置<br/>从配置中心加载<br/>添加到Environment"]
        RegisterListener["注册监听器<br/>配置变更监听<br/>热更新支持"]
    end
    
    subgraph AutoConfig["自动配置层 AutoConfiguration Layer"]
        ConfigValueAutoConfiguration["ConfigValueAutoConfiguration<br/>自动配置类<br/>ConditionalOnProperty"]
        ConfigValueBeanPostProcessor["ConfigValueBeanPostProcessor<br/>Bean后置处理器<br/>扫描@ConfigValue注解"]
    end
    
    subgraph Holder["持有者层 Holder Layer"]
        ConfigCenterHolder["ConfigCenterHolder<br/>配置中心持有者<br/>统一管理ConfigCenter实例"]
        ConfigCenterInstance["ConfigCenter实例<br/>全局单例<br/>避免重复创建"]
    end
    
    subgraph ConfigCenter["配置中心层 ConfigCenter Layer"]
        ConfigCenter["ConfigCenter接口<br/>统一配置中心接口"]
        NacosConfigCenter["NacosConfigCenter<br/>Nacos配置中心<br/>阿里云Nacos"]
        ApolloConfigCenter["ApolloConfigCenter<br/>Apollo配置中心<br/>携程Apollo"]
        ConsulConfigCenter["ConsulConfigCenter<br/>Consul配置中心<br/>HashiCorp Consul"]
        ZookeeperConfigCenter["ZookeeperConfigCenter<br/>Zookeeper配置中心<br/>Apache Zookeeper"]
    end
    
    subgraph Annotation["注解处理层 Annotation Processing Layer"]
        ConfigValueAnnotation["@ConfigValue注解<br/>配置值注入<br/>支持热更新"]
        ScanFields["扫描字段<br/>扫描@ConfigValue字段<br/>注入配置值"]
        ScanMethods["扫描方法<br/>扫描@ConfigValue方法<br/>注入配置值"]
    end
    
    subgraph HotReload["热更新层 HotReload Layer"]
        ConfigListener["ConfigListener<br/>配置变更监听器<br/>监听配置变化"]
        BindingInfo["BindingInfo<br/>绑定信息<br/>字段/方法与配置键绑定"]
        UpdateValue["更新值<br/>配置变更时<br/>自动更新字段值"]
        Callback["回调方法<br/>配置变更回调<br/>执行自定义逻辑"]
    end
    
    subgraph Publish["推送层 Publish Layer"]
        PublishConfig["推送配置<br/>publish配置<br/>强制推送"]
        PublishIfAbsent["推送配置不存在时<br/>publishIfAbsent配置<br/>仅当不存在时推送"]
    end
    
    SpringApp --> ConfigCenterConfigurationEnvironmentPostProcessor
    ConfigCenterConfigurationEnvironmentPostProcessor --> ConfigCenterProperties
    ConfigCenterProperties --> ConfigProps
    
    ConfigCenterConfigurationEnvironmentPostProcessor --> LoadConfig
    ConfigCenterConfigurationEnvironmentPostProcessor --> RegisterListener
    LoadConfig --> ConfigCenterHolder
    RegisterListener --> ConfigCenterHolder
    
    SpringApp --> ConfigValueAutoConfiguration
    ConfigValueAutoConfiguration --> ConfigCenterHolder
    ConfigCenterHolder --> ConfigCenterInstance
    
    ConfigValueAutoConfiguration --> ConfigValueBeanPostProcessor
    ConfigValueBeanPostProcessor --> ConfigValueAnnotation
    ConfigValueAnnotation --> ScanFields
    ConfigValueAnnotation --> ScanMethods
    
    ConfigCenterHolder --> ConfigCenter
    ConfigCenter --> NacosConfigCenter
    ConfigCenter --> ApolloConfigCenter
    ConfigCenter --> ConsulConfigCenter
    ConfigCenter --> ZookeeperConfigCenter
    
    ConfigValueBeanPostProcessor --> HotReload
    HotReload --> ConfigListener
    HotReload --> BindingInfo
    HotReload --> UpdateValue
    HotReload --> Callback
    
    ConfigValueBeanPostProcessor --> PublishConfig
    ConfigValueBeanPostProcessor --> PublishIfAbsent
    PublishConfig --> ConfigCenter
    PublishIfAbsent --> ConfigCenter
    
    style Application fill:#e3f2fd
    style Config fill:#fff3e0
    style PostProcessor fill:#f3e5f5
    style AutoConfig fill:#e8f5e9
    style Holder fill:#fce4ec
    style ConfigCenter fill:#fff9c4
    style Annotation fill:#e1f5fe
    style HotReload fill:#f1f8e9
    style Publish fill:#ffe0b2
```

### 2. 配置加载与初始化流程架构

```mermaid
%%{init: {'theme':'base', 'themeVariables': { 'primaryColor':'#fff'}}}%%
flowchart TD
    Start([开始: Spring Boot应用启动]) --> EnvironmentPostProcessor["ConfigCenterConfigurationEnvironmentPostProcessor<br/>环境后置处理器<br/>Ordered优先级执行"]
    
    EnvironmentPostProcessor --> ReadProperties["读取ConfigCenterProperties<br/>从Environment读取配置<br/>Binder.get绑定属性"]
    
    ReadProperties --> CheckEnabled{"检查<br/>plugin.config-center.enable配置"}
    
    CheckEnabled -->|"未启用"| EndSkip([结束: 跳过配置中心初始化])
    
    CheckEnabled -->|"已启用"| GetProtocol["获取协议类型<br/>protocol配置<br/>nacos/apollo/consul等"]
    
    GetProtocol --> CreateConfigCenter["创建ConfigCenter实例<br/>ServiceProvider.of<br/>根据协议创建对应实现"]
    
    CreateConfigCenter --> ConfigCenterType{"配置中心类型判断<br/>根据protocol"]
    
    ConfigCenterType -->|"nacos"| CreateNacos["创建NacosConfigCenter<br/>Nacos客户端<br/>Nacos SDK"]
    ConfigCenterType -->|"apollo"| CreateApollo["创建ApolloConfigCenter<br/>Apollo客户端<br/>Apollo SDK"]
    ConfigCenterType -->|"consul"| CreateConsul["创建ConsulConfigCenter<br/>Consul客户端<br/>Consul SDK"]
    ConfigCenterType -->|"zookeeper"| CreateZookeeper["创建ZookeeperConfigCenter<br/>Zookeeper客户端<br/>Zookeeper SDK"]
    
    CreateNacos --> StartConfigCenter
    CreateApollo --> StartConfigCenter
    CreateConsul --> StartConfigCenter
    CreateZookeeper --> StartConfigCenter
    
    StartConfigCenter["启动ConfigCenter<br/>configCenter.start<br/>连接配置中心"] --> SaveToHolder["保存到ConfigCenterHolder<br/>ConfigCenterHolder.setInstance<br/>全局单例管理"]
    
    SaveToHolder --> GetActiveProfile["获取激活环境<br/>spring.profiles.active<br/>或namespaceId配置"]
    
    GetActiveProfile --> LoadConfigurations["加载配置<br/>loadConfigurations方法<br/>从配置中心加载配置"]
    
    LoadConfigurations --> GetDataIds["获取DataId列表<br/>根据应用名称和环境<br/>构建配置键"]
    
    GetDataIds --> ProcessDataId["处理每个DataId<br/>循环处理每个配置"]
    
    ProcessDataId --> GetConfig["获取配置<br/>configCenter.getConfig<br/>从配置中心获取配置内容"]
    
    GetConfig --> ConfigFound{"配置是否存在"}
    
    ConfigFound -->|"不存在"| LogWarning["记录警告日志<br/>配置不存在"]
    
    ConfigFound -->|"存在"| ParseConfig["解析配置<br/>解析YAML/Properties<br/>转换为键值对"]
    
    ParseConfig --> AddToEnvironment["添加到Environment<br/>OriginTrackedMapPropertySource<br/>添加到PropertySources"]
    
    AddToEnvironment --> MoreDataIds{"是否还有更多<br/>DataId需要处理"}
    
    MoreDataIds -->|"是"| ProcessDataId
    MoreDataIds -->|"否"| CheckHotReload{"检查热更新配置<br/>hotReload.enabled"]
    
    LogWarning --> MoreDataIds
    
    CheckHotReload -->|"未启用"| EndInit([结束: 初始化完成])
    
    CheckHotReload -->|"已启用"| RegisterListener["注册配置监听器<br/>registerConfigListener<br/>监听配置变更"]
    
    RegisterListener --> SupportListener{"是否支持监听<br/>configCenter.isSupportListener()"}
    
    SupportListener -->|"不支持"| LogNoListener["记录日志<br/>配置中心不支持监听"]
    
    SupportListener -->|"支持"| AddListener["添加监听器<br/>configCenter.addListener<br/>注册配置变更监听"]
    
    AddListener --> EndInit
    LogNoListener --> EndInit
    
    style Start fill:#e1f5ff
    style EndSkip fill:#ffcdd2
    style EndInit fill:#c8e6c9
    style CheckEnabled fill:#ffccbc
    style ConfigCenterType fill:#ffccbc
    style ConfigFound fill:#ffccbc
    style MoreDataIds fill:#ffccbc
    style CheckHotReload fill:#ffccbc
    style SupportListener fill:#ffccbc
    style CreateConfigCenter fill:#fff9c4
    style LoadConfigurations fill:#fff9c4
    style AddListener fill:#fff9c4
```

### 3. @ConfigValue注解处理与热更新流程架构

```mermaid
%%{init: {'theme':'base', 'themeVariables': { 'primaryColor':'#fff'}}}%%
flowchart TD
    Start([开始: Bean初始化完成]) --> PostProcessAfterInit["ConfigValueBeanPostProcessor<br/>postProcessAfterInitialization<br/>Bean后置处理"]
    
    PostProcessAfterInit --> ScanBean["扫描Bean<br/>扫描所有字段和方法<br/>查找@ConfigValue注解"]
    
    ScanBean --> FoundAnnotation{"是否找到<br/>@ConfigValue注解"}
    
    FoundAnnotation -->|"未找到"| EndScan([结束: 扫描完成])
    
    FoundAnnotation -->|"找到注解"| ProcessAnnotation["处理注解<br/>解析注解信息<br/>获取配置键和默认值"]
    
    ProcessAnnotation --> ParseExpression["解析表达式<br/>parseExpression<br/>解析${key:defaultValue}格式"]
    
    ParseExpression --> CheckPublish{"检查推送配置<br/>publish或publishIfAbsent"]
    
    CheckPublish -->|"需要推送"| CheckSupportPublish{"配置中心是否<br/>支持推送<br/>isSupportPublish()"}
    
    CheckSupportPublish -->|"不支持"| LogNoPublish["记录警告日志<br/>配置中心不支持推送"]
    
    CheckSupportPublish -->|"支持"| GetPublishValue["获取推送值<br/>defaultValue或注解值<br/>获取要推送的配置值"]
    
    GetPublishValue --> PublishType{"推送类型判断<br/>publish或publishIfAbsent"]
    
    PublishType -->|"强制推送"| ForcePublish["强制推送配置<br/>configCenter.publish<br/>覆盖已存在配置"]
    
    PublishType -->|"不存在时推送"| PublishIfAbsent["推送配置不存在时<br/>configCenter.publishIfAbsent<br/>仅当不存在时推送"]
    
    ForcePublish --> InjectValue
    PublishIfAbsent --> InjectValue
    LogNoPublish --> InjectValue
    CheckPublish -->|"不需要推送"| InjectValue
    
    InjectValue["注入初始值<br/>从Environment获取配置值<br/>注入到字段或方法"] --> ResolveValue["解析配置值<br/>environment.getProperty<br/>获取配置值或默认值"]
    
    ResolveValue --> ConvertValue["转换值类型<br/>Converter.convertIfNecessary<br/>转换为目标类型"]
    
    ConvertValue --> FieldOrMethod{"字段或方法判断<br/>Field或Method"]
    
    FieldOrMethod -->|"字段"| InjectField["注入字段值<br/>field.set<br/>设置字段值"]
    
    FieldOrMethod -->|"方法"| InjectMethod["注入方法值<br/>method.invoke<br/>调用方法设置值"]
    
    InjectField --> CheckHotReload
    InjectMethod --> CheckHotReload
    
    CheckHotReload{"检查热更新<br/>annotation.hotReload()<br/>且hotReloadEnabled"]
    
    CheckHotReload -->|"未启用"| EndScan
    
    CheckHotReload -->|"已启用"| CheckSupportListener{"配置中心是否<br/>支持监听<br/>isSupportListener()"]
    
    CheckSupportListener -->|"不支持"| EndScan
    
    CheckSupportListener -->|"支持"| CreateBinding["创建绑定信息<br/>BindingInfo<br/>字段/方法与配置键绑定"]
    
    CreateBinding --> RegisterBinding["注册绑定<br/>registerBinding<br/>添加到bindingsByKey"]
    
    RegisterBinding --> CheckRegistered{"是否已注册<br/>监听器<br/>registeredListeners.contains"]
    
    CheckRegistered -->|"已注册"| EndScan
    
    CheckRegistered -->|"未注册"| AddListener["添加配置监听器<br/>configCenter.addListener<br/>注册ConfigValueListener"]
    
    AddListener --> AddToRegistered["添加到已注册集合<br/>registeredListeners.add<br/>避免重复注册"]
    
    AddToRegistered --> EndScan
    
    style Start fill:#e1f5ff
    style EndScan fill:#c8e6c9
    style FoundAnnotation fill:#ffccbc
    style CheckPublish fill:#ffccbc
    style CheckSupportPublish fill:#ffccbc
    style PublishType fill:#ffccbc
    style FieldOrMethod fill:#ffccbc
    style CheckHotReload fill:#ffccbc
    style CheckSupportListener fill:#ffccbc
    style CheckRegistered fill:#ffccbc
    style ProcessAnnotation fill:#fff9c4
    style InjectValue fill:#fff9c4
    style AddListener fill:#fff9c4
```

### 4. 配置变更热更新流程架构

```mermaid
%%{init: {'theme':'base', 'themeVariables': { 'primaryColor':'#fff'}}}%%
flowchart TD
    Start([开始: 配置中心配置变更]) --> ConfigChanged["配置变更事件<br/>ConfigCenter检测到配置变化<br/>触发监听器"]
    
    ConfigChanged --> ConfigValueListener["ConfigValueListener<br/>配置值监听器<br/>onUpdate或onDelete"]
    
    ConfigValueListener --> EventType{"事件类型判断<br/>onUpdate或onDelete"]
    
    EventType -->|"onUpdate"| GetBindingsUpdate["获取绑定信息<br/>bindingsByKey.get<br/>获取该配置键的所有绑定"]
    
    EventType -->|"onDelete"| GetBindingsDelete["获取绑定信息<br/>bindingsByKey.get<br/>获取该配置键的所有绑定"]
    
    GetBindingsUpdate --> ProcessBindingUpdate["处理每个绑定<br/>循环处理每个BindingInfo<br/>更新配置值"]
    
    GetBindingsDelete --> ProcessBindingDelete["处理每个绑定<br/>循环处理每个BindingInfo<br/>使用默认值"]
    
    ProcessBindingUpdate --> GetOldValue["获取旧值<br/>从字段获取当前值<br/>field.get(bean)"]
    
    ProcessBindingDelete --> UseDefaultValue["使用默认值<br/>binding.defaultValue<br/>配置删除时使用默认值"]
    
    GetOldValue --> UpdateValue["更新值<br/>injectFieldValue或injectMethodValue<br/>注入新值"]
    
    UseDefaultValue --> UpdateValue
    
    UpdateValue --> ConvertNewValue["转换新值类型<br/>Converter.convertIfNecessary<br/>转换为目标类型"]
    
    ConvertNewValue --> FieldOrMethod{"字段或方法判断<br/>Field或Method"]
    
    FieldOrMethod -->|"字段"| SetFieldValue["设置字段值<br/>field.set<br/>更新字段值"]
    
    FieldOrMethod -->|"方法"| InvokeMethodValue["调用方法值<br/>method.invoke<br/>调用方法更新值"]
    
    SetFieldValue --> LogUpdate["记录更新日志<br/>log.info<br/>记录配置变更信息"]
    
    InvokeMethodValue --> LogUpdate
    
    LogUpdate --> CheckCallback{"是否有回调方法<br/>binding.callback<br/>配置变更回调"]
    
    CheckCallback -->|"无回调"| EndUpdate([结束: 配置更新完成])
    
    CheckCallback -->|"有回调"| GetCallbackMethod["获取回调方法<br/>bean.getClass().getDeclaredMethod<br/>获取回调方法"]
    
    GetCallbackMethod --> MethodFound{"方法是否存在<br/>NoSuchMethodException"]
    
    MethodFound -->|"不存在"| LogNoMethod["记录警告日志<br/>回调方法不存在"]
    
    MethodFound -->|"存在"| InvokeCallback["调用回调方法<br/>callback.invoke<br/>执行自定义逻辑<br/>参数: key oldValue newValue"]
    
    LogNoMethod --> EndUpdate
    
    InvokeCallback --> EndUpdate
    
    style Start fill:#e1f5ff
    style EndUpdate fill:#c8e6c9
    style EventType fill:#ffccbc
    style FieldOrMethod fill:#ffccbc
    style CheckCallback fill:#ffccbc
    style MethodFound fill:#ffccbc
    style ConfigValueListener fill:#fff9c4
    style UpdateValue fill:#fff9c4
    style InvokeCallback fill:#fff9c4
```

> 💡 **提示**: 架构图支持横向滚动查看，也可以点击图表在新窗口中打开查看大图。

## 🔗 相关模块

- [spring-support-common-starter](../spring-support-common-starter) - 公共基础模块
- [spring-support-redis-starter](../spring-support-redis-starter) - Redis 缓存模块

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

---

**作者**: CH  
**版本**: 4.0.0.32  
**更新时间**: 2024/12/11
