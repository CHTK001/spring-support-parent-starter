# spring-support-datasync-starter

DataSync 的 Spring Boot 集成层：按 `plugin.datasync.*` 在启动时把映射配置（文件目录或数据库表）装配进 `DataSyncMappingManager`，交给 `DataSyncServer` 调度执行。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|---|---|---|
| 自动配置入口 | `DataSyncAutoConfiguration` | `META-INF/spring/...AutoConfiguration.imports` |
| 配置绑定 | `DatasyncProperties` | `plugin.datasync` |
| 映射管理 | `DataSyncMappingManager`、`DefaultDataSyncMappingManager` | Spring Bean |
| 映射调度端 | `DataSyncServer`、`DefaultDataSyncServer` | Spring Bean |
| Agent 服务端 | `AgentServerManager`、`DataSyncAgentServer` | Spring Bean（实例名 `default-server`） |
| 文件模式加载 | `DirectoryDataSyncMappingScanner` + `YamlConfigFileParser` / `JsonConfigFileParser` / `PropertiesConfigFileParser` | `plugin.datasync.mapping-source=file` |
| 数据库模式加载 | `DatabaseMappingLoader`（`JdbcTemplate` 一次性读取） | `plugin.datasync.database-mapping.enabled=true` |
| 建表与种子 | `db/init/V2.0__init_all_datasync.sql`、`db/init/V2.0__initdata_datasync.sql` | `spring-support-datasource-starter` 执行 |
| OSGi 服务桥（声明） | `com.chua.starter.datasync.support.spi.DynamicService` | OSGi Bundle 侧注册 |
| 服务信息 | `DataSyncAutoConfiguration#dataSyncServiceInfoContributor` | 服务信息注册表 |
| 本地启动验收入口 | `com.chua.starter.datasync.support.DataSyncApplication` | `--datasync.mode=file\|database` |

自动装配条件：classpath 存在 `com.chua.starter.datasync.DataSyncServer`（即引入了 `utils-support-datasync-starter`），且 `plugin.datasync.enabled` 为 `true`（缺省即 `true`）。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-datasync-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 作用域 | 说明 |
|---|---|---|
| `utils-support-datasync-starter` | compile | 核心接口：`DataSyncServer`、`DataSyncMappingManager`、扫描器、解析器 |
| `spring-support-datasource-starter` | compile | 执行 `db/init` 下的建表/种子脚本 |
| `spring-boot-starter-web`、`spring-webmvc` | compile | 本模块含 Web 集成，宿主为 Web 应用时直接生效 |
| `spring-support-osgi-starter`、`utils-support-osgi-starter` | compile | OSGi 服务桥（`DynamicService`）宿主 |
| `spring-support-ssh-starter` | compile | 仅在 pom 中声明，本模块源码未引用 |
| `utils-support-datalake-*`（core / mqtt / webhook / cdc / subscribe） | compile | 数据湖链路与被动推送入站适配器所需 |
| `utils-support-fory-starter`、`utils-support-protobuf-starter`、`utils-support-serialize-starter` | compile | 序列化能力 |
| `jackson-databind` | compile | 数据库 `params` JSON 解析 |
| `reactor-core`、`jspecify` | compile | 响应式引擎的传递依赖修正 |
| `mysql-connector-j` | runtime | 运行期驱动 |
| `utils-support-excel-starter` | test | Excel ↔ JDBC 同步探针 |

## 三、配置项

前缀：`plugin.datasync`（`DatasyncProperties`）。

| key | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用整个模块，同时是自动配置的开关 |
| `mapping-source` | `file` | 映射来源：`file` \| `database`；只决定是否装配文件扫描器 |
| `file-mapping.directory-path` | `./datasync/mappings` | 文件模式扫描目录 |
| `database-mapping.enabled` | `false` | 是否装配 `DatabaseMappingLoader` |
| `database-mapping.table-prefix` | `datasync_` | 表名前缀，实际读取 `{prefix}mapping` 与 `{prefix}field_mapping` |
| `database-mapping.cron` | `0/5 * * * * ?` | 声明字段，当前实现未消费（见第七节） |

本模块**没有** `auto-init-table` 之类的建表开关：表结构由 `db/init` 脚本随 `spring-support-datasource-starter` 落地。

### 文件格式

- YAML：`.yaml` / `.yml`
- JSON：`.json`
- Properties：`.properties`

目录会被递归遍历，仓库自带示例：`src/main/resources/datasync/mappings/user-sync.yaml`、`order-sync.json`。

`DirectoryDataSyncMappingScanner` 是普通类，`start()` 后先全量扫描，再通过 `WatchService`（2 秒轮询）监听目录变化做热加载；`stop()` 关闭线程与监听。

### 映射文件结构

```yaml
mappingId: user-sync
inputId: user-input
sourceId: user-source
outputId: user-output
sinkId: user-sink
batch: 10
cronType: fixed
cron: "0/5 * * * * ?"
params:
  key1: value1
mappings:
  - sourceField: name
    targetField: user_name
    converter: toString
  - sourceField: age
    targetField: user_age
    converter: toInteger
```

### 数据库表结构

由 `db/init/V2.0__init_all_datasync.sql` 创建（`V1.0__init_all_datasync.sql` 已并入并删除）：

```sql
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

## 四、快速开始

1. 引入依赖（会带入 `utils-support-datasync-starter` 与 `spring-support-datasource-starter`）。
2. 选择映射来源并配置路径或表前缀：

   ```yaml
   plugin:
     datasync:
       enabled: true
       mapping-source: file
       file-mapping:
         directory-path: ./config/datasync
   ```

3. 把映射文件放进该目录（或在库表写入映射行），启动应用后由 `DataSyncMappingManager` 持有全部映射，`DataSyncServer` 按映射的 `cron` 调度执行。

## 五、运行示例

### 5.1 调用示例

本模块无链式 API：`DatasyncProperties` 是纯 POJO，`DataSyncMappingManager` / `DataSyncServer` 的方法均为 `void` 或集合返回值，没有 `Builder` 与 `return this`。

```java
@Service
public class MappingBootstrap {

    @Resource
    private DataSyncMappingManager mappingManager;

    @Resource
    private DataSyncServer dataSyncServer;

    // 文件模式：自动装配的扫描器需要显式 start() 才会真正加载
    @Resource
    private DirectoryDataSyncMappingScanner scanner;

    public void load() {
        scanner.start();
        mappingManager.getMappings()
                .forEach(m -> System.out.println(m.mappingId() + " -> " + m.sinkId()));
    }

    public void run() {
        dataSyncServer.start();   // 本地验收入口里也是这样手动拉起
    }
}
```

数据库模式则改为写入表数据（`database-mapping.enabled=true` 时启动期自动读取）：

```sql
INSERT INTO datasync_mapping (datasync_mapping_id, datasync_mapping_input_id, datasync_mapping_source_id,
       datasync_mapping_output_id, datasync_mapping_sink_id, datasync_mapping_batch,
       datasync_mapping_cron_type, datasync_mapping_cron, datasync_mapping_params, datasync_mapping_enabled)
VALUES ('user-sync', 'user-input', 'user-source', 'user-output', 'user-sink', 100,
        'fixed', '0/5 * * * * ?', '{}', TRUE);

INSERT INTO datasync_field_mapping (datasync_field_mapping_id, datasync_field_mapping_mapping_id,
       datasync_field_mapping_source_field, datasync_field_mapping_target_field,
       datasync_field_mapping_converter, datasync_field_mapping_sort)
VALUES ('1', 'user-sync', 'name', 'user_name', 'toString', 0);
```

本地验收可直接跑模块自带启动类：

```
--datasync.mode=file        # 默认，扫描 src/main/resources/datasync/mappings
--datasync.mode=database    # 走数据库映射，默认嵌入式 H2
--datasync.db=mysql         # 数据库模式下改用真实 MySQL
```

### 5.2 SPI 扩展示例

本模块**不提供** `META-INF/extensions` 形式的 SPI 扩展点，扩展通过 Spring Bean 覆盖默认实现完成。

```java
// 1) 实现：自定义映射管理器（例如先做命名规整再入库）
public class AuditedMappingManager extends DefaultDataSyncMappingManager {
    @Override
    public void addMapping(DataSyncMapping mapping) {
        // 审计后委托父类
        super.addMapping(mapping);
    }
}

// 2) 登记：自动配置全部使用 @ConditionalOnMissingBean，同名 Bean 即可顶替
@Bean
public DataSyncMappingManager dataSyncMappingManager() {
    return new AuditedMappingManager();
}

// 3) 生效：文件扫描器与数据库加载器都注入 DataSyncMappingManager，改由自定义实现承载
```

可替换的装配点：`DataSyncMappingManager`、`DataSyncServer`、`AgentServerManager`、`DirectoryDataSyncMappingScanner`、`DatabaseMappingLoader`（按需覆盖同名 Bean）。

如需支持新的配置文件格式，实现 `com.chua.starter.datasync.scanner.ConfigFileParser`（`supports(Path)` + `parse(Path)`），再自行构造 `DirectoryDataSyncMappingScanner` 并传入解析器列表——本模块的自动配置固定只注册 YAML / JSON / Properties 三种解析器。

## 六、与其它 starter 的关系

- `utils-support-datasync-starter`：核心接口与扫描器/解析器实现所在处，本模块只做装配与配置绑定。
- `spring-support-datasource-starter`：执行 `db/init/V2.0__init_all_datasync.sql` 建表，数据库模式依赖它自动初始化。
- `spring-support-osgi-starter` / `utils-support-osgi-starter`：`DynamicService` 的 OSGi 服务宿主；普通 Spring 应用可忽略。
- `spring-support-sync-starter`：通过同步通道把数据交换结果下发到边缘节点，与本模块的映射加载互补。
- `spring-support-job-starter`：若需要把同步任务纳入统一调度台账，可由任务模块触发 `DataSyncServer`；本模块自身不依赖任务模块。

## 七、已知限制

- **没有 `plugin.datasync.database-mapping.auto-init-table` 这个配置项**，早期文档中的该键不成立；建表只能靠 `db/init` 脚本（或手工执行 SQL）。
- `database-mapping.cron` 只是声明字段，`DatabaseMappingLoader` 在 `afterPropertiesSet()` 里一次性读取映射，**不做轮询**；改表后需重启（或自行调用加载逻辑）才生效。
- `mapping-source` 与 `database-mapping.enabled` 是两个独立开关：`mapping-source=file` 且 `database-mapping.enabled=true` 会同时装配文件扫描器与数据库加载器，并非二选一。
- 数据库加载失败只记录错误日志（`从数据库加载 DataSync 映射失败`），不会阻断启动，排查时需关注日志。
- 数据库模式要求宿主自行提供 `DataSource` 与 JDBC 驱动；驱动虽以 `runtime` 引入 `mysql-connector-j`，其他数据库需自行补充。
- 模块自带的 `DataSyncApplication` 是**本地验收 demo**：内置示例 MySQL 连接串、并默认开启 SSH(2222) 与 OSGi Shell(6666) 的 demo 账号，不得当作生产配置模板。
- pom 会带入 ssh / osgi / datalake 全家桶，使用方应确认这些传递依赖是否可接受。
- `DynamicService` 仅接口声明，模块内没有对应 `META-INF` 注册文件，实际生效依赖 OSGi Bundle 侧注册服务。
