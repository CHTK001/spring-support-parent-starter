# spring-support-datasource-hub-starter

数据源中心模块（默认开启）：统一登记与管理被采集/被连接的数据源（JDBC / Redis / InfluxDB / Neo4j / Nacos / ZooKeeper / Nginx / Kafka / MongoDB / Elasticsearch / Prometheus / etcd / ClickHouse / DuckDB / SkyWalking 等），
提供数据源台账 CRUD、连通性探测、可复用的 JDBC 连接（空闲 TTL 缓存 + 跨方言元数据）、数据库 Web 管理器（navicat 风格）、SSH 隧道与中间件管理面。
独立于监控模块，供监控、采集等系统共享依赖。

**能力边界（先看这个）**：

- **能做什么**：台账管理、连通性探测（含 SSH 隧道中转）、JDBC 连接复用与 SQL 控制台、库表结构与数据浏览、DDL 执行、表复制、导入导出、结构快照与回滚、跨库合并查询（Calcite）、各中间件的管理页（MongoDB / Elasticsearch / ZooKeeper / Nacos / etcd / InfluxDB / Prometheus / Nginx / SkyWalking / Kafka）、数据源指标监控（SSE）、驱动 jar 与文件库上传。
- **明确不做什么**：不拦截运行时 SQL（它是管理面，不是 ORM 代理）；不为业务代码提供多数据源路由与 `@DS` 切换；不保证跨库事务；REST 接口无内置鉴权注解，暴露面由外部网关/oauth 决定（本模块不提供 `@Permission`）。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| 自动配置 | `DatasourceHubAutoConfiguration` | `@ComponentScan("...datasourcehub.support")` + `@MapperScan` + `plugin.datasource.hub.*` |
| 配置绑定 | `DatasourceHubProperties` | `plugin.datasource.hub.*` |
| 台账 CRUD / 连通性 / 类型 | `DatasourceController` `/ DatasourceTypeController` | `/ms/datasource` |
| 连接扩展点 SPI | `DatasourceConnector` / `DatasourceConnectorRegistry` | `META-INF/extensions/...DatasourceConnector`（app / jdbc / calcite / socket） |
| JDBC 建连与探活 | `JdbcDatasourceSupport` | 空闲 TTL 缓存 + SSH 隧道改写 + 方言指标 |
| 文件库路径守卫 | `FileJdbcPathGuard` | SQLite / H2 file / Derby / HSQLDB / DuckDB 路径白名单 |
| 口令加密存储 | `DatasourceSecrets` | AES-GCM `enc:v1:` 前缀 |
| 数据库 Web 管理器 | `DbConsoleController` | `/ms/monitor/db`（库表浏览 / SQL 控制台 / 结构维护 / 导入导出 / ER 图） |
| 结构快照与回滚 | `DbSchemaSnapshotService` / `DbConsoleController#snapshot*` | `/ms/monitor/db/table/{tableName}/schema-snapshots`（拍/列表/详情/回滚） |
| 表复制（跨库） | `DbTableCopyService` / `DbCopyTaskRegistry` | `/ms/monitor/db`（copy 端点） |
| 数据库文档导出 | `DbDocumentController` | `/ms/monitor/db/document`（`/formats`、`/export`、`/preview`） |
| 数据库 SPI 能力网关 | `DbSpiCapabilityController` | `/ms/monitor/db`（用户/权限/进程/慢查询，按方言 SPI） |
| SSH 隧道（高级配置） | `DatasourceTunnelService` / `DatasourceTunnelController` | `/ms/datasource/tunnel`（LOCAL / REMOTE / DYNAMIC） |
| 驱动 jar / 文件库上传 | `JdbcDriverJarController` / `FileDbUploadController` | `/ms/datasource/jdbc-driver`（别名 `/ms/datasource/driver-jar`）、`/ms/datasource/file-db` |
| 动态接口发布（契约） | `DynamicApiService`（接口 + DTO/VO/`MsDatasourceApi` 实体） | **仅声明契约，无实现与 REST 控制器**；两轨发布（embedded / standalone）的描述在接口 Javadoc 中，当前模块未装配 |
| 中间件管理（MongoDB / ES / ZK / Nacos / etcd / InfluxDB / Prometheus / Nginx / SkyWalking） | `*ManageController` + `*ManageServiceImpl` | `/ms/monitor/<type>` |
| 中间件数据源控制器 | `*DatasourceController`（Redis / Mongo / ES / Nginx / Neo4j / InfluxDB / Nacos / ZK / JDBC） | `/ms/datasource/<type>` |
| 中间件 SPI 分发 | `MiddlewareClientRegistry` / `MiddlewareDriverSupport` | `MiddlewareClientProvider` SPI + 上传 jar 后装 |
| 数据源指标（SSE + 调度） | `DatasourceMetricStreamRegistry` / `DatasourceMetricScheduler` | `/ms/datasource/metric`（订阅即采集） |
| Nginx 管理与指标 | `NginxManageService` / `NginxMetricScheduler` | `/ms/monitor/nginx` |
| 操作日志 | `DatasourceOperLogController` / `DatasourceOperLogScheduler` | `/ms/datasource/oper-log` |
| 服务信息登记 | `datasourceHubServiceInfoContributor` | 控制台模块能力 |
| 数据初始化 | `db/init/V2.0__init_all_datasource_hub.sql` | `ms_datasource` 等 9 张表（幂等） |

实际生效的能力：

- 数据源类型（`DatasourceHubConstants.DS_TYPES`）：`jdbc`、`redis`、`influxdb`、`neo4j`、`nacos`、`zookeeper`、`kafka`、`nginx`、`etcd`、`prometheus`、`mongodb`、`elasticsearch`、`clickhouse`、`duckdb`、`skywalking`；各类型默认端口在 `DS_DEFAULT_PORTS` 常量表（如 jdbc=3306、clickhouse=8123 走 HTTP、prometheus=9090、mongodb=27017）；
- JDBC 族统一走一条链路：`jdbc` / `clickhouse` / `duckdb`（`DS_JDBC_TYPES`）+ `calcite` 合并源；
- 文件库（`duckdb`）按文件语义处理：不设 TCP 超时不 TCP 探活，路径过 `FileJdbcPathGuard` 白名单（工作目录 / 临时目录 / 用户主目录 + 配置追加）；
- Calcite 合并数据源：把成员 `{"members":[id,...]}` 逐个建连装配成联邦 schema（重名自动加 `-2/-3` 后缀），支持跨库联合查询；合并记录自身不可编辑、没有单一 JDBC 连接串；
- 口令存储：`secret-key` 配置后新写入口令走 AES-GCM（`enc:v1:`）；未配置则明文入库并打一次性 WARN；历史 `b64:` 前缀兼容解码；
- 指标采集「有人看才采」：只探测 `DatasourceMetricStreamRegistry.watchedIds()`，无人订阅不产生探测流量，历史只覆盖被观测时段；
- 动态接口发布（**当前仅契约**）：`DynamicApiService` 接口定义了 `embedded`（动态注册进本应用 `RequestMappingHandlerMapping`，走系统 oauth 门控）/ `standalone`（独立端口起 HTTP 服务，AppKey 鉴权）两轨描述，但本模块只有接口 + DTO/VO + `MsDatasourceApi` 实体 + Mapper，**没有实现类与 REST 控制器**，运行期不可用。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-datasource-hub-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 |
|:-----|:-----|
| `spring-support-common-starter` | `ReturnResult` / `ServiceInfoContributor` / `ExpireValue` |
| `spring-support-datasource-starter` | 应用主数据源 |
| `spring-support-mybatis-starter` | MyBatis-Plus 映射器（`Ms*Mapper`） |
| `spring-boot-starter-web` | REST 控制器 / SSE / 文件上传 |
| `utils-support-calcite-starter` | Calcite 联邦内核（合并数据源的编译前提） |
| `utils-support-datasource-starter` | 中间件 SPI 契约（`MiddlewareClientProvider` / Capability / VO）+ 文档采集器 `DocumentParser` |
| `utils-support-kafka-starter` | Kafka 管理能力（SPI 实现，引进来即开放，删掉即未开放） |
| `utils-support-influxdb-starter` / `utils-support-zookeeper-starter` / `utils-support-skywalking-starter` | InfluxDB / ZooKeeper / SkyWalking 管理面（SPI 实现） |
| `utils-support-ssh-starter` | SSH 隧道客户端（`SshClient` / `SshTunnel`，LOCAL/REMOTE/DYNAMIC） |
| `spring-support-swagger-starter` | OpenAPI 文档 |
| `com.mysql:mysql-connector-j` / `com.h2database:h2` / `org.xerial:sqlite-jdbc` | MySQL + H2 + SQLite 运行时驱动（本地直跑/便携部署） |
| `com.clickhouse:clickhouse-jdbc:0.11.0`（all 分类器，runtime） | ClickHouse JDBC（HTTP 8123） |
| `org.duckdb:duckdb_jdbc:1.5.6.0`（runtime） | DuckDB 单文件嵌入式库 |
| `org.mongodb:mongodb-driver-sync:5.13.0`（+ core/bson 同版本钉齐） | MongoDB 官方同步驱动 |
| `org.apache.zookeeper:zookeeper:3.9.6` | ZooKeeper 客户端传递依赖钉版 |
| `spring-support-ai-starter`（optional） | text2sql（`/ms/monitor/db` 的 ai/status），宿主未引入时如实上报不可用 |
| `utils-support-word-starter` / `utils-support-pdf-starter`（optional） | 数据库文档 word/pdf 导出，未引入时端点 501 |
| `spring-api-support-common-tenant-starter`（optional） | 租户库连接口（`TenantDatasourceLookup` 契约，由 `DatasourceTenantLookupAdapter` 补实现） |

中间件能力（Kafka / InfluxDB / ZooKeeper / SkyWalking）以 SPI 形式按需装配：引对应 starter 即「开放」，去掉依赖即「未开放」（`MiddlewareClientRegistry` 对未装实现返回 `MiddlewareNotInstalledException` → 业务结果「功能未开放」，不落 500）。

## 三、配置项

配置前缀：**`plugin.datasource.hub`**（`DatasourceHubProperties`）。

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.datasource.hub.enabled` | `true` | 服务信息面板「数据源中心」展示旗标（**非模块开关**，不影响装配） |
| `plugin.datasource.hub.connection-ttl-minutes` | `10` | JDBC 连接空闲存活时长（分钟），超时自动关闭（小于 1 按 1 处理）；`ExpireValue` 惰性过期 + 后台清扫 |
| `plugin.datasource.hub.connection-sweep-interval-ms` | `60000` | 空闲连接后台清扫周期（毫秒）；**仅声明，运行期未读取**（实际清扫节拍写死在 `JdbcDatasourceSupport#sweepExpiredJdbcConnections` 的 `@Scheduled(fixedDelay=60_000L)`） |
| `plugin.datasource.hub.probe-timeout-ms` | `5000` | 连通性探测超时（毫秒） |
| `plugin.datasource.hub.connect-timeout-seconds` | `30` | JDBC 建连超时（秒）；实现按驱动族换算单位（MySQL/MariaDB/Oracle 毫秒、PostgreSQL 秒、SQL Server `loginTimeout`） |
| `plugin.datasource.hub.query-timeout-seconds` | `60` | SQL 执行超时（秒），0 = 不设上限；只约束语句级执行，不影响建连 |
| `plugin.datasource.hub.driver-jar-dir` | `data/jdbc-driver` | 上传的 JDBC 驱动 jar 落盘目录（工作目录下，避免临时目录被清理） |
| `plugin.datasource.hub.driver-jar-max-bytes` | `67108864`（64MB） | 驱动 jar 单个上限（字节） |
| `plugin.datasource.hub.file-db-upload-dir` | `data/file-db` | 文件库（SQLite/DuckDB/H2）库文件上传落盘目录 |
| `plugin.datasource.hub.file-db-max-bytes` | `67108864`（64MB） | 文件库库文件单个上限（字节） |
| `plugin.datasource.hub.file-jdbc-allowed-dirs` | 空列表 | 文件库 JDBC URL 允许的根目录白名单；留空默认放行工作目录 / 系统临时目录 / 用户主目录 |
| `plugin.datasource.hub.secret-key` | 空串 | 数据源口令加密密钥（原文，SHA-256 派生 AES-256）；**留空 = 明文入库**，配置后只影响之后写入的口令 |

## 四、快速开始

**第一步：引入依赖，准备一个主数据源（MySQL）**

DuckDB 与 H2/SQLite 驱动已随依赖在 runtime classpath，MySQL 驱动默认提供；要连其它数据库需自行引入对应驱动或走「驱动 jar 上传」。

**第二步：配置 yaml（最小可用）**

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/datasource_hub?useUnicode=true&characterEncoding=UTF-8&useSSL=false
    username: root
    password: ""
plugin:
  datasource:
    script:
      enable: true          # 框架数据源脚本执行：db/init 下 V2.0__init_all_datasource_hub.sql 建 9 张表
  datasource:
    hub:
      # enabled: true       # 面板展示旗标，不影响装配
      # secret-key: ${DS_HUB_SECRET}   # 建议配置，否则口令明文入库
```

**第三步：验证**

- 启动后 `GET /actuator/health` UP；`ms_datasource` 等表自动创建（幂等，9 张）；
- 台账 CRUD：`GET /ms/datasource` → `POST /ms/datasource`（body 带 `datasourceType`/`datasourceName`/`datasourceHost`/`datasourcePort`/`datasourceDatabase`/`datasourceUsername`/`datasourcePassword`）→ `POST /ms/datasource/{id}/test` 验证连通性；
- 打开数据库管理台：`GET /ms/monitor/db/tables?datasourceId=1`（或 `0` 表示应用主库）；
- 新增合并数据源：`POST /ms/datasource/merge {"name":"合并库","memberIds":[1,2]}` 后，`GET /ms/monitor/db/tables?datasourceId=<mergeId>` 即可在该合并源下做跨库联合查询（SQL 里以成员 schema 名限定表，如 `SELECT * FROM mysql_member.sys_user UNION ...`）。

## 五、运行示例

### 5.1 调用示例

**数据库管理台（navicat 风格 REST）**：

```bash
# 列库表（datasourceId=0 表示应用主库）
curl http://127.0.0.1:8080/ms/monitor/db/tables?datasourceId=0

# SQL 控制台执行查询
curl -X POST http://127.0.0.1:8080/ms/monitor/db/execute \
  -H "Content-Type: application/json" \
  -d '{"datasourceId": 1, "sql": "SELECT * FROM sys_user LIMIT 10"}'

# 建表 DDL（结构维护在 /ms/datasource 前缀下）
curl -X POST http://127.0.0.1:8080/ms/datasource/ddl/execute \
  -H "Content-Type: application/json" \
  -d '{"datasourceId": 1, "datasourceDatabase": "app", "sql": "CREATE TABLE t(id BIGINT)"}'

# 表数据浏览
curl "http://127.0.0.1:8080/ms/monitor/db/table/sys_user/data?datasourceId=1"
```

**SSH 隧道（目标库内网不可直连时）**：`POST /ms/datasource/tunnel` 保存 `{datasourceId, sshHost, sshPort, sshUsername, sshAuthType(PASSWORD|KEY), sshPassword/sshPrivateKey, localPort, bindAddress, tunnelType(LOCAL|REMOTE|DYNAMIC)}`，
之后对同一数据源的探测与 SQL 执行自动走隧道本地端口；隧道按数据源维度缓存，应用关闭时统一释放。

**数据源指标 SSE**（订阅即开启该数据源的定时探测，每 60s 一轮，推送最近 30 点）：

```bash
curl -N http://127.0.0.1:8080/ms/datasource/metric/stream?datasourceId=1
# 首帧 ready，随后每 60s 一帧 metric；心跳注释帧每 20s
```

### 5.2 SPI 扩展示例

本模块**定义**两个扩展点：

**① 连接扩展点 `DatasourceConnector`**（`@Spi("datasource-connector")`）：

| 实现 | 别名 | 接管范围 | order |
|:-----|:-----|:---------|:------|
| `AppDatasourceConnector` | `app` | 应用主库（复用容器 `DataSource` bean） | 1000 |
| `JdbcDatasourceConnector` | `jdbc` | 台账 JDBC 族（jdbc / clickhouse / duckdb） | 200 |
| `CalciteDatasourceConnector` | `calcite` | Calcite 合并源（成员装配联邦连接） | 300 |
| `SocketDatasourceConnector` | `socket` | 非 JDBC 中间件（仅 TCP 探活） | — |

`DatasourceConnectorRegistry` 按 `order` 降序逐个 `supports(type)` 分发。注册方式：实现类带 `@Spi` 注解 + `META-INF/extensions/com.chua.starter.datasourcehub.support.spi.connector.DatasourceConnector` 里写 `别名=实现类全限定名`；SPI 以无参构造创建、`SpringServiceAutowire` 补字段注入，因此依赖必须写成 `@Autowired` 字段。

新增一种数据源：提交一个实现类 + 注册别名即可，不必改服务层与 Controller。

**② 中间件契约（utils 层）**：`com.chua.datasource.support.middleware.MiddlewareClientProvider` —— 实现方各自 starter 注册，宿主（本模块）只认契约；已注册实现清单见 `GET /ms/monitor/middleware/types`。新增中间件同样只提交实现，不写本模块代码。

替换既有的默认实现（例如换掉 Nginx 的执行器），替换对应 `NginxExecutor` / `NginxCommandProvider` Bean 即可（本模块无其它隐藏装配点）。

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
|:-----|:-----|:-----|
| `spring-support-datasource-starter` | 本模块依赖 | 应用主数据源与动态路由；`AppDatasourceConnector` 复用它容器里的 `DataSource` bean |
| `spring-support-mybatis-starter` | 本模块依赖 | 台账持久层（`Ms*Mapper`） |
| `spring-support-collect-starter` | 可组合（采集平台） | 采集任务的「数据源」取自本模块台账；`DatasourceConnector` 拆分的直接动因是让采集复用同一套建连细节 |
| `spring-support-monitor-starter` | 同源不同模块 | 数据库管理台与全部 `Db*` 支撑类从 monitor 迁到本模块，URL 保持 `/ms/monitor/db/**` 不变；监控与采集各自依赖本模块即得同一套控制台 |
| `spring-support-ai-starter` | optional | text2sql（`/ms/monitor/db/ai/status`），未引入时如实上报不可用 |
| `spring-support-oauth-client-starter` | 不依赖 | 本模块 REST 接口**不带** `@Permission`；暴露面由部署侧网关/oauth 决定 |
| `spring-support-eduai-starter` | 可组合 | eduai 用 `spring-support-datasource-starter` 做单库；采集平台（eduai 的采集管理）依赖本模块管数据源 |
| 易混淆：`spring-support-datasource-hub-starter` vs `spring-support-datasource-starter` | 后者是「连接基础设施」（路由/切换/池），**前者是管理面**（台账/控制台/指标/中间件管理），不要用 hub 去做业务多数据源路由 |

## 七、已知限制

- **动态接口未实现**：`DynamicApiService` 只有接口与模型，无 `implements` 实现、无 Controller，`/ms/datasource/api/**` 不存在；Javadoc 里的两轨发布（embedded / standalone）是设计描述，当前代码不可调用。
- **`connection-sweep-interval-ms` 未生效**：`DatasourceHubProperties` 声明了该 key，但清扫节拍写死在 `JdbcDatasourceSupport#sweepExpiredJdbcConnections` 的 `@Scheduled(fixedDelay = 60_000L)`，改配置不会改变清扫周期。
- **文件库路径白名单只认本地文件形态**：`FileJdbcPathGuard` 对 `mem:` / `tcp://` / `hsql://` / `res:` 等非本地形态直接放行（它们不落磁盘）；白名单默认放行 工作目录 / 临时目录 / 用户主目录，要放行其它目录（如 `/data/dbs`）必须显式追加 `file-jdbc-allowed-dirs`。
- **`secret-key` 配了不能换**：更换密钥会让此前 `enc:v1:` 密文全部解不开（解密返回空串 + WARN），那些数据源口令只能重新录入；未配密钥时明文入库并每进程打一次 WARN。
- **指标历史只覆盖被观测时段**：采集是「订阅即采」，新建数据源第一次打开监控页签时曲线在一轮采集前为空（前端据 `message` 显示），不是数据丢失。
- **动态接口 `standalone` 轨鉴权独立**：该能力当前未装配（见上）；若将来由其它模块补上实现，`embedded` 走应用自身 oauth 门控、`standalone` 独立端口起 `Server`（鉴权靠 AppKey），两者安全边界不同——**当前代码两者都不可用**。
- **文档导出按 classpath 探测**：word/pdf 渲染器是 optional 依赖（pdf 还要求服务端装 `wkhtmltopdf`），未引入时 `/ms/monitor/db/document/formats` 不列出、`/export` 返回 501，前端应据 `formats` 隐藏入口。
- **非 JDBC 中间件只做 TCP 探活**：`SocketDatasourceConnector` 仅验证端口可达，不验证协议/凭据；结构能力由各 `*ManageController` 的专用客户端承担。
- **合并数据源无单一连接串**：`CalciteDatasourceConnector#jdbcInfo` 抛 `UnsupportedOperationException`（成员是装配出来的）；`create`/`update` 也拒绝外部直接写这类记录。
- **REST 接口无内置鉴权**：本模块控制器不含 `@Permission`，台账中可能存有口令（未配密钥时为明文）；生产环境必须把 `/ms/datasource/**`、`/ms/monitor/db/**`、`/ms/monitor/middleware/**` 置于网关鉴权之后。
- **Nginx 管理依赖目标主机**：`LocalNginxExecutor`（裸进程 / systemd）与 `SshNginxExecutor` 二选一，写配置/重载/重启全部作用于目标主机，路径与命令不受本模块约束（`nginx` 数据源行的配置根目录默认 `/etc/prometheus` 之类字段由类型语义决定）。
- **调度周期写死**：指标采集 60s 一轮（`DatasourceMetricScheduler`）、SSE 心跳 20s、操作日志清理每天 04:40、指标清理每天 04:20、Nginx 指标 30s —— 均为 `@Scheduled` 常量，不走配置，且故意不用 `@Value` 占位符（宿主早期创建链上 resolver 未就绪会炸启动）。