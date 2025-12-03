# Spring Support MyBatis Tenant Starter

## 📖 简介

基于 MyBatis-Plus 的多租户插件，提供自动租户隔离功能。通过 SQL 拦截器自动在查询、更新、删除等操作中添加租户条件，实现数据隔离。

## ✨ 核心功能

- **自动租户隔离**：基于 MyBatis-Plus 拦截器，自动在 SQL 中添加租户条件
- **智能表过滤**：支持配置忽略特定表的租户过滤
- **自动表结构更新**：可选功能，自动检测并为数据库表添加租户字段
- **灵活配置**：支持自定义租户字段名、忽略表列表等

## 📦 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-mybatis-tenant-starter</artifactId>
    <version>4.0.0.34</version>
</dependency>
```

## 🚀 快速开始

### 1. 配置文件

在 `application.yml` 或 `application.properties` 中添加配置：

```yaml
plugin:
  mybatis-plus:
    tenant:
      # 是否启用租户功能
      enable: true
      # 是否自动添加租户字段（生产环境请谨慎使用）
      auto-add-column: false
      # 租户ID字段名
      tenant-id: sys_tenant_id
      # 忽略的表（这些表不会被租户拦截器过滤）
      ignore-table:
        - sys_user
        - sys_role
        - sys_config
```

### 2. 设置租户 ID

在请求处理中设置当前租户 ID，通常在拦截器或过滤器中实现：

```java
import com.chua.starter.common.support.utils.RequestUtils;

// 设置当前请求的租户ID
RequestUtils.setTenantId("1001");
```

### 3. 使用示例

配置完成后，所有的 MyBatis 查询都会自动添加租户条件：

```java
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    public List<User> getAllUsers() {
        // 自动添加 WHERE sys_tenant_id = '当前租户ID'
        return userMapper.selectList(null);
    }

    public User getUserById(Long id) {
        // 自动添加 WHERE sys_tenant_id = '当前租户ID' AND id = ?
        return userMapper.selectById(id);
    }
}
```

## ⚙️ 配置说明

### 基础配置项

| 配置项                                       | 类型        | 默认值        | 说明                       |
| -------------------------------------------- | ----------- | ------------- | -------------------------- |
| `plugin.mybatis-plus.tenant.enable`          | Boolean     | false         | 是否启用租户功能           |
| `plugin.mybatis-plus.tenant.auto-add-column` | Boolean     | false         | 是否自动添加租户字段到表中 |
| `plugin.mybatis-plus.tenant.tenant-id`       | String      | sys_tenant_id | 租户 ID 字段名             |
| `plugin.mybatis-plus.tenant.ignore-table`    | Set<String> | 空集合        | 忽略的表列表               |

### 同步协议配置项

| 配置项                                      | 类型    | 默认值               | 说明                                   |
| ------------------------------------------- | ------- | -------------------- | -------------------------------------- |
| `sync-protocol.enable`                      | Boolean | false                | 是否启用同步协议                       |
| `sync-protocol.type`                        | String  | client               | 程序类型：server-服务端，client-客户端 |
| `sync-protocol.protocol`                    | String  | websocket-sync       | 协议类型，支持 websocket-sync 等       |
| `sync-protocol.server-host`                 | String  | 0.0.0.0              | 服务端主机地址（服务端模式）           |
| `sync-protocol.server-port`                 | Integer | 19280                | 服务端端口                             |
| `sync-protocol.server-address`              | String  | ws://localhost:19280 | 服务端地址（客户端模式）               |
| `sync-protocol.heartbeat`                   | Boolean | true                 | 是否启用心跳                           |
| `sync-protocol.heartbeat-interval`          | Integer | 30                   | 心跳间隔（秒）                         |
| `sync-protocol.connect-timeout`             | Integer | 10000                | 连接超时时间（毫秒）                   |
| `sync-protocol.reconnect-interval`          | Integer | 5                    | 重连间隔（秒）                         |
| `sync-protocol.max-reconnect-attempts`      | Integer | -1                   | 最大重连次数，-1 表示无限重连          |
| `sync-protocol.metadata-sync.enable`        | Boolean | false                | 是否启用元数据同步                     |
| `sync-protocol.metadata-sync.interval`      | Integer | 300                  | 同步间隔（秒）                         |
| `sync-protocol.metadata-sync.initial-delay` | Integer | 60                   | 初始延迟（秒）                         |

### 自动添加租户字段

⚠️ **警告**：`auto-add-column` 功能会自动修改数据库表结构，建议仅在开发环境使用。

当启用 `auto-add-column` 时，系统会：

1. 启动时扫描数据库所有表
2. 检查每张表是否包含租户字段
3. 如果缺少租户字段，自动添加该字段
4. 字段属性：
   - 类型：Integer
   - 可空：是
   - 索引：是
   - 注释：租户 ID

## 🔌 租户同步协议

租户同步协议基于 `SyncProtocol` 实现长连接通信，支持实时元数据推送。

### 服务端配置

```yaml
plugin:
  mybatis-plus:
    tenant:
      enable: true
      sync-protocol:
        enable: true
        type: server
        protocol: websocket-sync
        server-host: 0.0.0.0
        server-port: 19280
        heartbeat: true
        heartbeat-interval: 30
        metadata-sync:
          enable: true
          interval: 300
          initial-delay: 60
```

### 客户端配置

```yaml
plugin:
  mybatis-plus:
    tenant:
      enable: true
      sync-protocol:
        enable: true
        type: client
        protocol: websocket-sync
        server-address: ws://localhost:19280
        heartbeat: true
        heartbeat-interval: 30
        reconnect-interval: 5
        max-reconnect-attempts: -1
        metadata-sync:
          enable: true
          interval: 300
          initial-delay: 60
```

### 实现元数据提供者（服务端）

```java
import com.chua.common.support.annotations.Spi;
import com.chua.tenant.support.sync.TenantMetadataProvider;

/**
 * 管理员账号元数据提供者
 */
@Spi("admin-account")
public class AdminAccountMetadataProvider implements TenantMetadataProvider {

    @Override
    public String getName() {
        return "admin-account";
    }

    @Override
    public boolean supports(String tenantId) {
        return true;
    }

    @Override
    public Map<String, Object> getMetadata(String tenantId) {
        Map<String, Object> metadata = new HashMap<>();
        // 从数据库获取租户的管理员账号信息
        metadata.put("adminAccount", getAdminAccount(tenantId));
        metadata.put("services", getEnabledServices(tenantId));
        return metadata;
    }
}
```

### 实现元数据消费者（客户端）

```java
import com.chua.common.support.annotations.Spi;
import com.chua.tenant.support.sync.TenantMetadataConsumer;

/**
 * 管理员账号元数据消费者
 */
@Spi("admin-account")
public class AdminAccountMetadataConsumer implements TenantMetadataConsumer {

    @Override
    public String getName() {
        return "admin-account";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void consumeMetadata(String tenantId, Map<String, Object> metadata) {
        // 更新本地管理员账号信息
        Object adminAccount = metadata.get("adminAccount");
        if (adminAccount != null) {
            updateLocalAdminAccount(tenantId, (Map<String, Object>) adminAccount);
        }

        // 更新启用的服务列表
        Object services = metadata.get("services");
        if (services != null) {
            updateEnabledServices(tenantId, (List<String>) services);
        }
    }
}
```

### SPI 配置

在 `META-INF/services/` 目录下创建配置文件：

**服务端**：`com.chua.tenant.support.sync.TenantMetadataProvider`

```
com.example.AdminAccountMetadataProvider
```

**客户端**：`com.chua.tenant.support.sync.TenantMetadataConsumer`

```
com.example.AdminAccountMetadataConsumer
```

## 🔧 高级用法

### 忽略特定表

某些系统表或公共数据表不需要租户隔离，可以配置忽略：

```yaml
plugin:
  mybatis-plus:
    tenant:
      ignore-table:
        - sys_user # 用户表
        - sys_role # 角色表
        - sys_dict # 字典表
        - sys_config # 配置表
```

### 自定义租户字段名

如果你的数据库使用不同的租户字段名：

```yaml
plugin:
  mybatis-plus:
    tenant:
      tenant-id: tenant_code # 使用 tenant_code 作为租户字段
```

### 动态设置租户 ID

通常在拦截器或过滤器中根据请求信息设置租户 ID：

```java
@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler) {
        // 从请求头获取租户ID
        String tenantId = request.getHeader("X-Tenant-Id");

        // 或从 Token 中解析租户ID
        // String tenantId = JwtUtils.getTenantIdFromToken(token);

        if (StringUtils.isNotBlank(tenantId)) {
            RequestUtils.setTenantId(tenantId);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                               HttpServletResponse response,
                               Object handler,
                               Exception ex) {
        // 清理租户ID，避免线程池复用导致的问题
        RequestUtils.clearTenantId();
    }
}
```

## 📝 注意事项

1. **非 Web 环境**：在非 Web 请求上下文（如定时任务、异步任务）中，租户拦截器会自动忽略所有表
2. **性能考虑**：租户字段建议添加索引以提高查询性能
3. **数据迁移**：如果是在已有数据的表上启用租户功能，需要手动为历史数据设置租户 ID
4. **事务处理**：租户 ID 在整个事务中保持一致，确保在事务开始前设置租户 ID
5. **生产环境**：建议关闭 `auto-add-column`，手动管理表结构变更

## 🐛 常见问题

### Q1: 为什么查询结果为空？

**A**: 检查是否正确设置了租户 ID，可以通过日志查看：

```yaml
logging:
  level:
    com.chua.tenant.support.configuration: DEBUG
```

### Q2: 某些表不需要租户隔离怎么办？

**A**: 将这些表添加到 `ignore-table` 配置中。

### Q3: 如何在定时任务中使用？

**A**: 定时任务中需要手动设置租户 ID：

```java
@Scheduled(cron = "0 0 1 * * ?")
public void scheduledTask() {
    List<String> tenantIds = getTenantIds();
    for (String tenantId : tenantIds) {
        RequestUtils.setTenantId(tenantId);
        try {
            // 执行业务逻辑
            doSomething();
        } finally {
            RequestUtils.clearTenantId();
        }
    }
}
```

### Q4: 租户 ID 为 -1 是什么意思？

**A**: 当无法获取租户 ID 时，系统会使用 -1 作为默认值，这通常表示：

- 未在请求中设置租户 ID
- 非 Web 请求上下文
- 租户 ID 获取失败

## 📊 日志说明

启用 DEBUG 级别日志可以查看详细的租户处理信息：

```yaml
logging:
  level:
    com.chua.tenant.support.configuration: DEBUG
```

日志示例：

```
[租户插件] 初始化租户拦截器，租户字段: sys_tenant_id
[租户插件] 当前租户ID: 1001
[租户插件] 表 sys_user 在忽略列表中
[租户插件] 共扫描到 25 张表
[租户插件] 共为 3 张表添加了租户字段
```

## 🔗 相关链接

- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [多租户设计模式](https://docs.microsoft.com/zh-cn/azure/architecture/patterns/sharding)

## 📄 许可证

本项目采用 Apache License 2.0 许可证。

## 👥 贡献

欢迎提交 Issue 和 Pull Request！

---

**作者**: CH  
**版本**: 1.0.0  
**更新时间**: 2024/12/02
