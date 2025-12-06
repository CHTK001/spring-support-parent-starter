# Spring Support MyBatis Tenant Starter

基于 MyBatis-Plus 的多租户解决方案，支持服务端/客户端模式，通过 Sync 协议实现租户数据自动同步。

## 功能特性

- ✅ **多租户数据隔离** - 自动在 SQL 中注入租户条件
- ✅ **服务端/客户端模式** - 灵活的部署架构
- ✅ **自动添加租户字段** - 客户端模式自动为数据库表添加租户字段
- ✅ **数据同步** - 服务端数据变更自动下发到客户端
- ✅ **可扩展接口** - 提供 Handler 和 Consumer 接口便于业务扩展

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│              服务端 (spring-api-support-system-starter)      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  TenantMetadataProvider  →  收集租户元数据           │   │
│  │  TenantServerConfiguration  →  服务端配置           │   │
│  │  SysTenantMapper  →  租户数据操作                   │   │
│  └─────────────────────────────────────────────────────┘   │
│                           │                                 │
│                           │ Sync 协议 (RSocket/WebSocket)   │
│                           ▼                                 │
│  ┌─────────────────────────────────────────────────────┐   │
│              客户端 (spring-api-support-tenanted-starter)    │
│  │  TenantMetadataConsumer  →  消费租户元数据           │   │
│  │  TenantClientConfiguration  →  自动添加租户字段      │   │
│  │  TenantHandler  →  处理租户数据更新                 │   │
│  │  TenantServiceHandler  →  处理服务/菜单数据更新     │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-mybatis-tenant-starter</artifactId>
    <version>${version}</version>
</dependency>
```

### 2. 配置文件

#### 服务端配置 (管理平台)

```yaml
plugin:
  # 同步协议配置
  sync:
    enable: true
    type: server
    server:
      port: 19380
      protocol: rsocket

  # 租户配置
  mybatis-plus:
    tenant:
      enable: true
      mode: server
      tenant-id-column: sys_tenant_id
      ignore-table:
        - sys_config
        - sys_dict
      sync:
        enable: true
```

#### 客户端配置 (租户应用)

```yaml
plugin:
  # 同步协议配置
  sync:
    enable: true
    type: client
    client:
      server-host: 192.168.1.100 # 服务端地址
      server-port: 19380
      protocol: rsocket
      auto-register: true

  # 租户配置
  mybatis-plus:
    tenant:
      enable: true
      mode: client
      auto-add-column: true # 自动添加租户字段到数据库表
      tenant-id-column: sys_tenant_id
      ignore-table:
        - sys_config
      sync:
        enable: true
        default-tenant-id: "1" # 当前租户ID
```

## 配置属性说明

| 属性                                                | 说明                           | 默认值          |
| --------------------------------------------------- | ------------------------------ | --------------- |
| `plugin.mybatis-plus.tenant.enable`                 | 是否启用租户功能               | `false`         |
| `plugin.mybatis-plus.tenant.mode`                   | 运行模式：`server`/`client`    | `client`        |
| `plugin.mybatis-plus.tenant.auto-add-column`        | 是否自动添加租户字段（客户端） | `false`         |
| `plugin.mybatis-plus.tenant.tenant-id-column`       | 租户 ID 字段名                 | `sys_tenant_id` |
| `plugin.mybatis-plus.tenant.ignore-table`           | 忽略的表（不添加租户条件）     | `[]`            |
| `plugin.mybatis-plus.tenant.sync.enable`            | 是否启用租户同步               | `false`         |
| `plugin.mybatis-plus.tenant.sync.default-tenant-id` | 默认租户 ID                    | `null`          |
| `plugin.mybatis-plus.tenant.sync.interval`          | 同步间隔（秒）                 | `300`           |

## 扩展接口

### 1. TenantHandler - 租户数据处理器

客户端实现此接口以处理从服务端同步的租户数据：

```java
@Component
public class CustomTenantHandler implements TenantHandler {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    public void saveOrUpdate(SysTenant tenant) {
        // 处理租户数据更新
        // 例如：创建/更新租户管理员账号
        log.info("收到租户更新: {}", tenant.getSysTenantCode());
    }

    @Override
    public void delete(SysTenant tenant) {
        // 处理租户删除
        log.info("收到租户删除: {}", tenant.getSysTenantCode());
    }
}
```

### 2. TenantServiceHandler - 服务数据处理器

客户端实现此接口以处理从服务端同步的服务/菜单数据：

```java
@Component
public class CustomTenantServiceHandler implements TenantServiceHandler {

    @Autowired
    private SysMenuMapper sysMenuMapper;

    @Override
    public void saveOrUpdate(Integer sysTenantId, List<Integer> menuIds) {
        // 处理服务/菜单数据更新
        // 例如：更新本地菜单权限
        log.info("收到菜单更新: 租户={}, 菜单数={}", sysTenantId, menuIds.size());

        // 清除旧菜单
        sysMenuMapper.removeAllByTenantId(sysTenantId);

        // 添加新菜单
        for (Integer menuId : menuIds) {
            sysMenuMapper.saveForTenant(menuId, sysTenantId);
        }
    }

    @Override
    public void delete(Integer sysTenantId, List<Integer> menuIds) {
        // 处理菜单删除
        sysMenuMapper.removeByTenantId(sysTenantId, menuIds);
    }
}
```

### 3. TenantMetadataConsumer - 元数据消费者

客户端实现此接口以消费从服务端推送的自定义元数据：

```java
@Component
@Spi("custom")
public class CustomMetadataConsumer implements TenantMetadataConsumer {

    @Override
    public String getName() {
        return "custom";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public void consumeMetadata(String tenantId, Map<String, Object> metadata) {
        // 处理自定义元数据
        Object customData = metadata.get("customData");
        if (customData != null) {
            log.info("收到自定义数据: 租户={}, 数据={}", tenantId, customData);
            // 业务处理...
        }
    }

    @Override
    public boolean supports(String metadataType) {
        return "custom".equals(metadataType);
    }
}
```

### 4. TenantMetadataProvider - 元数据提供者

服务端实现此接口以提供需要下发的自定义元数据：

```java
@Component
@Spi("custom")
public class CustomMetadataProvider implements TenantMetadataProvider {

    @Override
    public String getName() {
        return "custom";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public Map<String, Object> getMetadata(String tenantId) {
        Map<String, Object> metadata = new HashMap<>();

        // 收集需要下发的数据
        metadata.put("customData", getCustomDataForTenant(tenantId));
        metadata.put("config", getTenantConfig(tenantId));

        return metadata;
    }

    @Override
    public boolean supports(String tenantId) {
        return true;
    }
}
```

## 使用场景

### 场景一：SaaS 多租户平台

1. **管理平台** 配置为 `mode: server`，管理所有租户信息
2. **租户应用** 配置为 `mode: client`，自动接收租户数据
3. 管理平台修改租户信息后，自动同步到对应的租户应用

### 场景二：单租户应用自动配置

1. 配置为 `mode: client`，设置 `auto-add-column: true`
2. 应用启动时自动为数据库表添加租户字段
3. 所有 SQL 自动注入租户条件，实现数据隔离

## 数据同步流程

```
服务端更新租户数据
       │
       ▼
TenantMetadataProvider 收集元数据
       │
       ▼
通过 Sync 协议推送到客户端
       │
       ▼
TenantMetadataConsumer 接收并解析
       │
       ├──► TenantHandler.saveOrUpdate() 处理租户数据
       │
       └──► TenantServiceHandler.saveOrUpdate() 处理服务数据
```

## 目录结构

```
com.chua.tenant.support
├── common/                    # 通用模块
│   ├── configuration/         # 自动配置
│   ├── entity/               # 实体类
│   └── properties/           # 配置属性
│
├── client/                   # 客户端模块
│   ├── configuration/        # 客户端配置（自动添加字段）
│   ├── consumer/             # 元数据消费者接口
│   └── handler/              # 数据处理器接口
│
└── server/                   # 服务端模块
    ├── configuration/        # 服务端配置
    ├── mapper/               # 数据访问层
    └── provider/             # 元数据提供者接口
```

## 注意事项

1. **数据安全**：确保 Sync 协议通信加密，防止数据泄露
2. **字段添加**：`auto-add-column` 会修改数据库结构，生产环境谨慎使用
3. **表忽略**：系统表和配置表应添加到 `ignore-table` 中
4. **同步延迟**：数据同步存在一定延迟，关键业务需考虑最终一致性

## 依赖模块

- `spring-support-mybatis-starter` - MyBatis-Plus 基础模块
- `spring-support-sync-starter` - 同步协议模块（可选）

## 版本历史

### v1.0.0 (2024-12-06)

- 初始版本
- 支持服务端/客户端模式
- 支持自动添加租户字段
- 支持数据同步功能

## License

Apache License 2.0
