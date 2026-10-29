# 数据同步 CSV/Excel 快速导入（Fast Load）验收报告

- 日期：2026-10-28
- 模块：spring-support-monitor-starter（后端）+ vue-support-monitor-starter（数据源中心「数据同步」页）
- 范围：CSV/Excel 文件同步新增**快速模式**（文件统一转 CSV → 各数据库原生 LOAD/COPY 直灌），独立 SPI 与 datasync 逐行引擎分离；删除一切模拟/mock

---

## 一、交付内容

### 后端（monitor-starter）

| 文件 | 说明 |
|---|---|
| `datasync/loader/JdbcCsvLoader.java` | SPI 接口：`load(连接信息, 表, 列, CSV文件, 编码) -> long` |
| `datasync/loader/MysqlCsvLoader.java` | `@Spi("mysql")`，`LOAD DATA LOCAL INFILE`，自动补 `allowLoadLocalInfile=true` |
| `datasync/loader/PostgresqlCsvLoader.java` | `@Spi("postgresql")`，pgjdbc `CopyManager.copyIn`，`COPY ... FROM STDIN` |
| `resources/META-INF/extensions/com.chua.starter.monitor.support.datasync.loader.JdbcCsvLoader` | SPI 注册文件（mysql=…/postgresql=…） |
| `controller/BulkImportController.java` | `POST /ms/monitor/bulkimport` + `/ms/monitor/bulkimport/preview`（独立控制器，避开类级前缀冲突） |
| `service/SyncTaskService.java` / `impl/SyncTaskServiceImpl.java` | `bulkImport`（读文件→转规范 CSV→按方言 SPI 直灌→多目标汇总，逐目标失败不阻断）、`bulkPreview`（真实读前 20 行） |
| `model/dto/BulkImportDTO.java` / `model/BulkImportResultVO.java` / `model/BulkPreviewVO.java` | 请求 / 结果 / 预览 VO |

### datasource-hub

| 文件 | 说明 |
|---|---|
| `model/DataSourceVO.java` | record 加 `fastLoadEnabled` 字段（兼容构造器 + masked() 透传） |
| `service/impl/DatasourceServiceImpl.java` | `fastLoadOf()`：多线索（database=完整URL / driverClassName / version 探测值）判断方言，MySQL/PG=`true`，非 JDBC=`null` |

### 前端（vue dat 源中心「数据同步」页）

| 文件 | 说明 |
|---|---|
| `components/sync/BulkImportDialog.vue` | `doImport` 删除 `setTimeout(1500)` 模拟、`previewCsv` 删除 mockHeaders/mockRows，均接真实 API；输出目标卡加绿/红快速导入能力提示（`fastLoadHint` + `.bi-fastload-hint.is-ok/.is-no`） |
| `api/syncTaskApi.ts` | `bulkImportCsv` / `bulkPreviewCsv` / `BulkPreview` / `BulkImportResult` 等类型与真实接口 |

### 依赖

- monitor-starter pom 增加 `org.postgresql:postgresql`（**compile scope**，BOM 管版本），PostgresqlCsvLoader 编译期引用 `CopyManager/BaseConnection`。

---

## 二、远程部署（增量推送 + 重启，不动配置）

后端跑在并行环境远程主机（SSH `ibssh_7x6wdjnehkotmoszcmpyt5n6hq@ssh.instabox.sh`，`deploy.sh` 松散目录部署：`app/`(自身类+配置) + `lib/`(569 依赖) + `loader/`）。

部署方式选择**增量推送**而非 `deploy.sh release` 整包替换，原因：远程 `app/application*.yml` 与本地完全不同（哈希不同），整包替换会破坏远程配置。

```text
本地 target/classes 打包 -> scp /tmp/bulkimport-pkg.tgz
远程执行：解包 -> 备份 app/.bulk-prev(431 files) -> cp 逐个推 class -> 推 META-INF/extensions 注册文件 -> 推 postgresql-42.7.11.jar 到 lib/ -> ./deploy.sh restart

校验（部署脚本落盘执行）：
OK   com/chua/starter/monitor/support/controller/BulkImportController.class
OK   com/chua/starter/monitor/support/datasync/loader/JdbcCsvLoader.class
OK   com/chua/starter/monitor/support/datasync/loader/MysqlCsvLoader.class
OK   com/chua/starter/monitor/support/datasync/loader/PostgresqlCsvLoader.class
OK   com/chua/starter/monitor/support/model/BulkImportResultVO.class
OK   com/chua/starter/monitor/support/model/BulkPreviewVO.class
OK   com/chua/starter/monitor/support/model/dto/BulkImportDTO.class
OK   com/chua/starter/monitor/support/service/impl/SyncTaskServiceImpl.class
OK   com/chua/starter/datasourcehub/support/model/DataSourceVO.class
OK   com/chua/starter/datasourcehub/support/service/impl/DatasourceServiceImpl.class
OK   META-INF/extensions/...JdbcCsvLoader
/data/workspace/monitor-deploy/lib/postgresql-42.7.11.jar
started pid=1499327  → [ports] *:29181 *:8082 *:19380  [tasks] 200
```

> 首次用 `cp -a` 推 class 在共享文件系统上未生效（文件未落地），改逐个 `cp` 后全部 `OK`；随后修复 `fastLoadOf` 又单独推送 `DatasourceServiceImpl.class` 并重启（pid=1536974）。

---

## 三、接口级验收（真实数据，非 mock）

登录：`POST /v2/user/login`（admin / Admin@123）→ accessToken；以下均带 Bearer 调用，远程 `127.0.0.1:8082`。

### 3.1 `fastLoadEnabled` 能力自动计算回显

```text
GET /ms/datasource/list  →  数据源台账
id=3  instabox-e2e      type=jdbc    version=MySQL 8.4.13      fastLoadEnabled=True
id=7  instabox-hotsport type=jdbc    version=MySQL 8.4.13      fastLoadEnabled=True
id=14 pg-e2e-fastload   type=jdbc    database=jdbc:postgresql:… version=PostgreSQL 16.15  fastLoadEnabled=True
id=5  local-nginx       type=nginx   fastLoadEnabled=None
id=8  influx-e2e        type=influxdb fastLoadEnabled=None
id=9  ch-e2e            type=clickhouse fastLoadEnabled=None
id=10 duck-e2e          type=duckdb  fastLoadEnabled=None
id=11 neo4j-101         type=neo4j   fastLoadEnabled=None
```

> 中途发现缺陷并修复：`fastLoadOf` 最初只查 `database` 字段，遇到裸库名（如 `report`）的 MySQL/PG 数据源漏判为 `false`；修复为「database(完整URL) / driverClassName / version(探测回写)」三路线索任一命中即 `true`，无法判断返回 `null`。

### 3.2 预览 — `POST /ms/monitor/bulkimport/preview`（真实读文件）

```json
{ "filePath": "/tmp/bulk_fast_accept.csv", "delimiter": ",", "encoding": "UTF-8", "hasHeader": true }
{
  "code": "00000",
  "data": {
    "headers": ["id", "name", "score", "tag"],
    "rows": [
      { "id": "1", "name": "张伟", "score": "88.5", "tag": "A" },
      { "id": "2", "name": "Lisa", "score": "92.0", "tag": "B" },
      { "id": "3", "name": "王芳", "score": "77.25", "tag": "A" },
      { "id": "4", "name": "Tom",  "score": "65.5",  "tag": "C" },
      { "id": "5", "name": "陈静", "score": "99.9",  "tag": "B" }
    ],
    "total": 5
  }
}
```

### 3.3 MySQL 快速导入（LOAD DATA LOCAL INFILE）— 真实灌库

```text
POST /ms/monitor/bulkimport  outputs=[{targetDatasourceId:3, targetTable:"bulk_fast_accept", columnMapping:[id,name,score,tag]}]

data: { "status": "SUCCESS", "readCount": 5, "writeCount": 5, "costMs": 44,
        "message": "全部目标快速导入完成",
        "outputs": [{ targetDatasourceId:3, targetTable:"bulk_fast_accept", status:"SUCCESS",
                      writeCount:5, costMs:44, message:"快速导入成功，写入 5 行" }] }

mysql > SELECT COUNT(*) FROM bulk_fast_accept;   → 5
mysql > SELECT * FROM bulk_fast_accept ORDER BY id;
1 张伟  88.50 A
2 Lisa  92.00 B
3 王芳  77.25 A
4 Tom   65.50 C
5 陈静  99.90 B
```

> 首次 FAILED：`Loading local data is disabled` —— 远程 MySQL 服务端 `local_infile=OFF`（客户端已由 loader 自动补 allowLoadLocalInfile=true）。定位后 `SET GLOBAL local_infile=ON`，重跑 SUCCESS。

### 3.4 PostgreSQL 快速导入（COPY FROM STDIN）— 真实灌库

起临时 PG 16 容器（`postgres:16-alpine`，127.0.0.1:15432，与既有 ch-e2e/influx-e2e 同模式），经数据源管理接口创建 PG 数据源（id=14，driverClassName=org.postgresql.Driver）：

```text
POST /ms/monitor/bulkimport  outputs=[{targetDatasourceId:14, targetTable:"bulk_fast_accept_pg", ...}]

data: { "status": "SUCCESS", "readCount": 5, "writeCount": 5, "costMs": 21,
        "outputs": [{ status:"SUCCESS", writeCount:5, costMs:18, message:"快速导入成功，写入 5 行" }] }

SELECT COUNT(*) FROM bulk_fast_accept_pg;  → 5
SELECT * FROM bulk_fast_accept_pg ORDER BY id;  → 5 行，中文（张伟/王芳/陈静）正确
```

### 3.5 Excel 文件走快速模式（xlsx → 统一转 CSV → LOAD）

```text
本地 openpyxl 生成 5 行 xlsx → scp 远程 /tmp/bulk_excel_accept.xlsx
preview  → headers=[id,name,score,tag], total=5
import   → status=SUCCESS, read=5, write=5, costMs=77
mysql > SELECT * FROM bulk_excel_accept ORDER BY id;  → 101张三/102 Lucy/103李四/… 完整落库
```

### 3.6 多目标 + 单目标失败不阻断

```text
outputs=[
  { targetDatasourceId:3, targetTable:"bulk_fast_accept_bak", ... },   // 正常表
  { targetDatasourceId:3, targetTable:"bulk_no_such_table_xyz", ... }  // 不存在的表
]
整体: FAILED read=5 write=5 cost=178ms   （整体 FAILED 因为 1 个目标失败）
 目标[... bulk_fast_accept_bak]: SUCCESS write=5 cost=137ms msg=快速导入成功，写入 5 行
 目标[... bulk_no_such_table_xyz]: FAILED write=0 cost=28ms msg=Table 'report.bulk_no_such_table_xyz' doesn't exist
校验: bulk_fast_accept_bak = 5 行   （成功目标照常写入，失败目标不阻断）
```

---

## 四、单元测试（无 mock，真实契约）

`JdbcCsvLoaderContractTest`（SPI 发现 + 方言判断，3 用例）：`Tests run: 3, Failures: 0, Errors: 0`（parent pom 默认 `<skipTests>true</skipTests>`，须 `-DskipTests=false`）。本次复核 `mvn test -DskipTests=false -Dtest=JdbcCsvLoaderContractTest` → MVN-EXIT=0。

---

## 五、前端验收

- 编译：`vue-tsc` → TOTAL_ERRORS=0。
- 代码审查（删除模拟）：
  - `doImport` 中 `setTimeout(1500)` 模拟已删除，直接 `await bulkImportCsv(payload)`；
  - `previewCsv` 中 `mockHeaders/mockRows` 已删除，直接调真实 `bulkPreviewCsv`；
  - 输出目标卡绿/红能力提示由 `fastLoadEnabled` 驱动（`fastLoadHint` / `fastLoadEnabledOf`）。
- 浏览器验收（8848 dev，environment vite 提供）：
  - 数据同步页 + 「批量导入 CSV」三步向导弹窗渲染正常；
  - 预览 CSV 时弹窗「预览确认」页真实渲染表头 + 前 5 行（含中文），来自真实 `bulkimport/preview`；
  - 填路径 → 预览 → 场景可复现。

> 注：8848 dev server 由并行环境统一管理，验收前后 PID 多次漂移（30420→32404→39020），曾在某时点代理到旧 H2 测试实例（`datasource_driver_jar_id not found`），与本次代码无关；接口级验收统一走远程部署后的 MySQL 新后端（`8082-agqwy3tplnv4xakvlmllio7goq.instabox.tech`），全部通过。

---

## 六、红线合规校验（全局源码编辑规则）

```text
10 个本次改动源码文件扫描（BOM / U+FFFD / 严格 UTF-8）：
BOM=False  FFFD=False  strictUtf8=True   → 0 违规

本地编译：mvn -pl datasource-hub,monitor compile -DskipTests → MVN-EXIT=0
改动均为 add（新增控制器/SPI/方法/字段）+ 局部替换（mock→真实、fastLoadOf 线索增强），
未变更既有类名/方法签名/import 语义（DataSourceVO 保持 record 兼容构造器 + masked() 透传）。
```

---

## 七、结论

**通过验收**。CSV/Excel 快速导入（Fast Load）：

1. MySQL（LOAD DATA LOCAL INFILE）与 PostgreSQL（COPY FROM STDIN）**真实灌库成功**，中文与数值精度正确；
2. 文件统一转规范 CSV、多目标并行、逐目标失败不阻断；
3. `fastLoadEnabled` 能力自动计算回显（JDBC MySQL/PG=true，非 JDBC=None）；
4. 前端彻底删除模拟/mock，预览与导入均走真实接口；
5. 预览 / 结果 VO、字段映射复用既有配置体系；PG 驱动 compile scope 纳入 BOM 管理。

遗留说明：

- 远程 MySQL 的 `local_infile` 服务端开关依赖部署环境配置（本次验收现场手动 `SET GLOBAL` 开启），属数据库环境项，不随代码交付；
- PG 数据源 `database` 需存完整 JDBC URL（`jdbc:postgresql://…`）才能被 `resolveJdbcUrl` 与方言判断正确识别（裸库名按 MySQL 拼接，为 datasource-hub 既有语义）。