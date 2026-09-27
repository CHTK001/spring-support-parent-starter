# spring-support-datasource-hub-starter

> 数据源中心：统一登记与管理被监控 / 被采集的数据源，独立于监控模块，供监控、采集等系统共享。

## 简介

`spring-support-datasource-hub-starter` 提供：

- **数据源台账**：JDBC / Redis / InfluxDB / Neo4j / Nacos / ZooKeeper / Nginx 的统一登记（CRUD）；
- **连通性探测**：JDBC 真实建连校验，其余类型 TCP 探测；
- **可复用 JDBC 连接**：空闲 TTL 缓存（`ExpireValue`）+ 屏蔽 close 的连接代理，避免反复建连；
- **跨方言支撑**（`JdbcDialectSupport`）：库切换（MySQL `USE` / H2/PG `setSchema`）、库/表/列存在性、
  建删库 DDL、改列、列注释，供数据源管理与数据库管理器复用。

## 依赖

| 依赖 | 说明 |
|---|---|
| `spring-support-common-starter` | 公共基础 |
| `spring-support-datasource-starter` | 应用主数据源（基础设施型，非本模块） |
| `spring-support-mybatis-starter` | MyBatis-Plus |
| `spring-support-swagger-starter` | OpenAPI 3 |
| `utils-support-common-starter` | `ReturnResult` / `ExpireValue` |

## 表

| 表 | 用途 |
|---|---|
| `ms_datasource` | 数据源台账 |

建表脚本：`src/main/resources/db/init/V1.0__init_all_datasource_hub.sql`（MySQL / H2 双库可执行子集）。

> 宿主需在自己的 `spring.sql.init.schema-locations` 中追加该脚本（或由迁移工具管理）。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/ms/datasource`、`/ms/datasource/list` | 数据源列表 |
| GET | `/ms/datasource/{id}` | 数据源详情 |
| POST | `/ms/datasource` | 新增 |
| PUT | `/ms/datasource/{id}` | 更新（自动清退旧连接） |
| DELETE | `/ms/datasource/{id}` | 删除（自动清退旧连接） |
| POST | `/ms/datasource/{id}/test` | 连通性测试 |
| GET | `/ms/datasource/types` | 支持的类型列表 |

## 配置

```yaml
datasource:
  hub:
    enabled: true                      # 服务信息面板旗标（非模块开关）
    connection-ttl-minutes: 10         # JDBC 连接空闲存活时长（分钟）
    probe-timeout-ms: 5000             # 连通性探测超时
```

## 编程接入

```java
// 注入 DatasourceService
Connection conn = datasourceService.connection(datasourceId); // 空闲 TTL 缓存；close 为空操作
ReturnResult<DataSourceVO> ds = datasourceService.get(id);
```

## 版本

- 当前版本：`4.0.0.42`
- 父模块：`spring-support-parent-starter`
- 作者：CH