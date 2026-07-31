# spring-support-datasync-starter

DataSync Spring Boot Starter，提供 DataSync 映射配置的 Spring Boot 自动配置，支持**文件目录扫描**和**数据库表加载**两种模式。

## 模块简介

`spring-support-datasync-starter` 是 `utils-support-datasync-starter` 的 Spring Boot 集成模块，负责：

- 通过 `plugin.datasync.*` 配置 DataSync 行为
- 启动时自动扫描指定目录下的 YAML/JSON/Properties 映射文件
- 从数据库表加载映射配置，适配 `spring-support-datasource-starter` 自动建表
- 向 `DataSyncServer` 注册映射，供调度器执行

## 依赖关系

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-datasync-starter</artifactId>
</dependency>
```

依赖传递引入：
- `utils-support-datasync-starter`（核心接口）
- `spring-support-datasource-starter`（provided，数据源支持）
- `jackson-databind`（JSON 解析）

## 配置说明

### 基础配置

| 属性 | 说明 | 默认值 |
|:---|:---|:---|
| `plugin.datasync.enabled` | 是否启用 DataSync | `true` |
| `plugin.datasync.mapping-source` | 映射来源：`file` \| `database` | `file` |

### 文件模式配置

| 属性 | 说明 | 默认值 |
|:---|:---|:---|
| `plugin.datasync.file-mapping.directory-path` | 映射配置文件目录路径 | `./datasync/mappings` |

支持的文件格式：
- YAML：`.yaml` / `.yml`
- JSON：`.json`
- Properties：`.properties`

### 数据库模式配置

| 属性 | 说明 | 默认值 |
|:---|:---|:---|
| `plugin.datasync.database-mapping.enabled` | 是否启用数据库映射源 | `false` |
| `plugin.datasync.database-mapping.table-prefix` | 表名前缀 | `datasync_` |
| `plugin.datasync.database-mapping.auto-init-table` | 是否自动初始化表结构 | `true` |
| `plugin.datasync.database-mapping.cron` | 数据库轮询间隔（Cron） | `0/5 * * * * ?` |

#### 数据库表结构

```sql
-- 映射主表
CREATE TABLE IF NOT EXISTS datasync_mapping (
    datasync_mapping_id VARCHAR(255) PRIMARY KEY,
    datasync_mapping_input_id VARCHAR(255),
    datasync_mapping_source_id VARCHAR(255),
    datasync_mapping_output_id VARCHAR(255),
    datasync_mapping_sink_id VARCHAR(255),
    datasync_mapping_batch INT DEFAULT 100,
    datasync_mapping_cron_type VARCHAR(255),
    datasync_mapping_cron VARCHAR(255),
    datasync_mapping_params TEXT,
    datasync_mapping_enabled BOOLEAN DEFAULT TRUE,
    datasync_mapping_create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    datasync_mapping_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 字段映射表
CREATE TABLE IF NOT EXISTS datasync_field_mapping (
    datasync_field_mapping_id VARCHAR(255) PRIMARY KEY,
    datasync_field_mapping_mapping_id VARCHAR(255),
    datasync_field_mapping_source_field VARCHAR(255),
    datasync_field_mapping_target_field VARCHAR(255),
    datasync_field_mapping_converter VARCHAR(255),
    datasync_field_mapping_sort INT,
    FOREIGN KEY (datasync_field_mapping_mapping_id) REFERENCES datasync_mapping(datasync_mapping_id)
);
```

## 使用示例

### 文件模式

```yaml
# application.yml
plugin:
  datasync:
    enabled: true
    mapping-source: file
    file-mapping:
      directory-path: ./config/datasync
```

目录结构：
```
config/datasync/
├── user-sync.yaml
├── order-sync.json
└── product-sync.properties
```

### 数据库模式

```yaml
# application.yml
plugin:
  datasync:
    enabled: true
    mapping-source: database
    database-mapping:
      enabled: true
      table-prefix: datasync_
      auto-init-table: true
```

数据库插入示例：
```sql
INSERT INTO datasync_mapping (datasync_mapping_id, datasync_mapping_input_id, datasync_mapping_source_id, datasync_mapping_output_id, datasync_mapping_sink_id, datasync_mapping_batch, datasync_mapping_cron_type, datasync_mapping_cron, datasync_mapping_params, datasync_mapping_enabled)
VALUES ('user-sync', 'user-input', 'user-source', 'user-output', 'user-sink', 100, 'fixed', '0/5 * * * * ?', '{}', TRUE);

INSERT INTO datasync_field_mapping (datasync_field_mapping_id, datasync_field_mapping_mapping_id, datasync_field_mapping_source_field, datasync_field_mapping_target_field, datasync_field_mapping_converter, datasync_field_mapping_sort)
VALUES ('1', 'user-sync', 'name', 'user_name', 'toString', 0);
```

## 与 DataSync 核心模块关系

```
spring-support-datasync-starter
├── DatasyncProperties（配置绑定）
├── DataSyncAutoConfiguration（自动装配）
├── DatabaseMappingLoader（数据库加载）
└── DirectoryDataSyncMappingScanner（文件扫描，委托给 utils-support-datasync-starter）

utils-support-datasync-starter
├── DataSyncServer（核心接口）
├── DefaultDataSyncServer（默认实现）
├── DataSyncMappingManager（映射管理）
└── scanner（ConfigFileParser / DirectoryDataSyncMappingScanner）
```

## 注意事项

- 数据库模式需要用户项目自行引入 JDBC 驱动（如 MySQL、PostgreSQL、H2）
- 文件模式会递归扫描子目录
- 数据库模式默认每 5 分钟轮询一次（可通过 `cron` 调整）
- 数据库表结构由 `spring-support-datasource-starter` 自动执行 `src/main/resources/db/init/V1.0.0__init_datasync_mapping.sql` 建表，无需手动执行
