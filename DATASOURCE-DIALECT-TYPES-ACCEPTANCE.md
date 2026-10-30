# 数据源设计表多方言支持 —— 交付说明

> 日期：2026-10-27
> 范围：三项需求（文件库上传 / VECTOR 类型 / 设计表按方言显示类型）
> 状态：**代码完成、自动化验收全绿、沙箱部署完成、8848 界面验收三连全部通过（证据见 §4）**

---

## 一、改动清单

### 后端（spring-support-datasource-hub-starter）

| 文件 | 改动 |
|---|---|
| `dbconsole/DbColumnDef.java` | 类型白名单扩容到 8 个方言（新增 BYTEA/TIMESTAMPTZ/INTERVAL/JSONB、NUMBER/VARCHAR2/NVARCHAR2/NCHAR/CLOB/NCLOB/RAW/BINARY_FLOAT/BINARY_DOUBLE、MONEY/SMALLMONEY/NVARCHAR/NTEXT/IMAGE/DATETIME2/SMALLDATETIME/DATETIMEOFFSET/UNIQUEIDENTIFIER/XML、UTINYINT/USMALLINT/UINTEGER/UBIGINT/HUGEINT）；`SIZE_REQUIRED` 增加 VARCHAR2/NVARCHAR2/NCHAR/NVARCHAR/RAW/VECTOR；`SCALE_ALLOWED` 增加 NUMBER；新增 `VECTOR`（维度必填、上限 16383） |
| `service/FileDbUploadService.java` | 新增：文件库库文件上传（扩展名白名单、大小上限、内容指纹落盘、路径穿越净化），落 `data/file-db` |
| `controller/FileDbUploadController.java` | 新增：`POST /ms/datasource/file-db/upload` |
| `model/FileDbUploadVO.java` | 新增：上传结果（原名/大小/绝对路径） |
| `config/DatasourceHubProperties.java` | 新增 `file-db-upload-dir`（默认 `data/file-db`）、`file-db-max-bytes`（默认 64MB） |

### 前端（pages/datasource）

| 文件 | 改动 |
|---|---|
| `utils/dbDialect.ts` | 新增：方言识别 `detectDialect`（类型键 → URL 前缀 → 驱动类名），面板与设计表共用 |
| `utils/columnTypes.ts` | 类型分组按方言化：mysql/postgresql/oracle/sqlserver/sqlite/duckdb/h2/clickhouse 各一套；`SIZE_REQUIRED`/`SCALE_ALLOWED` 同步；新增 `columnTypeGroupsFor`/`ALL_COLUMN_TYPES` |
| `views/console/DatabaseManagerView.vue` | 设计网格/列属性弹窗按 `currentDialect` 取类型表；`COLUMN_TYPE_ALIAS` 改为「方言表优先、别名兜底、跨方言保留原名」，不再有 `CLOB→TEXT`、`DATE→DATETIME`、`INTEGER→INT` 这类毁类型的硬映射；未知类型补「其他」不再污染全局常量 |
| `components/console/DesignColumnGrid.vue` | 新增 `dialect` prop，类型下拉按方言 |
| `components/console/CreateTableDialog.vue` | 透传 `dialect` |
| `metrics/DatasourceServerStatsPanel.vue` | 复用 `dbDialect`（去掉内联重复判断） |
| `api/index.ts` | 新增 `uploadFileDb` |
| `views/ScDataSourceView.vue` | DuckDB「库文件路径」与 jdbc+SQLite/H2 的库名字段旁新增「上传库文件」按钮，传完自动回填绝对路径 |

### 测试（新增）

- 后端 `DbColumnDefDialectTypeTest`（4 用例）：各方言类型可构造、VECTOR 维度规则、未知类型拒绝、新类型缺长度拒绝
- 后端 `FileDbUploadServiceTest`（5 用例）：正常落盘回绝对路径、超 64MB 拒绝、扩展名白名单、`../` 穿越净化、空文件拒绝
- 后端 `DatasourceCreateValidateTest`（4 用例，本轮验收新发现后补）：DuckDB + port=0 通过端口校验、JDBC + port=0 仍拒绝、JDBC + 合法端口通过、DuckDB + 非零端口通过
- 前端 `tests/columnTypes.test.mjs`（扩充到 9 用例）：8 方言分组自洽、方言归属不串味、SQLite 仅 5 存储类、VECTOR 归属与维度、SIZE_REQUIRED 镜像等

---

## 二、自动化验收证据（本机执行）

### 后端全量测试

```text
mvn -pl spring-support-datasource-hub-starter test -DskipTests=false
→ EXIT=0；13 个测试类 64 个用例全部通过（Failures: 0, Errors: 0）
其中：
  DbColumnDefDialectTypeTest        Tests run: 4  ✓
  FileDbUploadServiceTest           Tests run: 5  ✓
  DatasourceCreateValidateTest      Tests run: 4  ✓   ← 本轮验收新发现补测
```

### 前端测试与类型检查

```text
node --test tests/columnTypes.test.mjs
→ tests 9, pass 9, fail 0

apps/vue-support-monitor-starter: node_modules/.bin/vue-tsc --noEmit -p tsconfig.json
→ EXIT=0
```

### 接口级（临时本地实例实测，已停止；结果如下）

```text
POST /ms/datasource/file-db/upload  accept.duckdb
  → ok, path=…\data\file-db\9445663a1e52-accept.duckdb, size=536576
POST …/upload  notadb.txt
  → 只接受库文件（db / db3 / sqlite / sqlite3 / duckdb / mv.db / h2.db / sdb）
POST …/upload  big.duckdb (65MB)
  → 库文件超过大小上限 64MB
POST …/upload  filename=../../evil.duckdb
  → 落盘名 .._.._evil.duckdb（指纹前缀 + 净化，无目录穿越）
```

---

## 三、行为口径（改了什么、为什么）

1. **文件库上传不走 pages/filesystem**：那套是对象存储分片上传（provider/bucket/任务/SSE），落点是 bucket；SQLite/DuckDB/H2 要的是**服务器上可写绝对路径**。直传落到受管目录，返回路径本身就在 `fileJdbcAllowedDirs` 白名单内，填进表单即可建连。
2. **VECTOR**：MySQL 9 与 pgvector 都用 `VECTOR(n)`，维度就是长度，必填，上限 16383（取两者安全交集）。DDL 拼成 `VECTOR(1536)`。白名单放行后，不支持的内核报错原样透出到设计表弹窗（不吞成「不支持的列类型」）。
3. **设计表按方言**：下拉只出现当前内核认的类型；未知方言回落到 MySQL。别名表只做兜底且不再改动类型语义。
4. **DuckDB 无端口**：前端表单无主机/端口（`port` 占位 0）；后端对文件库类型（`isFileJdbcType`）跳过「端口需为 1~65535」校验，否则「上传→保存」会被误拒。

---

## 四、界面验收三连（8848 实测，全部通过）

> 环境：后端已部署到 instabox 沙箱 agqwy3tplnv4xakvlmllio7goq（SSH ibssh_7x6wdjnehkotmoszcmpyt5n6hq），
> 8848 dev server 代理指向该实例；登录 admin/Admin@123。以下均为实际操作输出。

### ① 文件库上传 → 路径自动回填 → 保存

```text
新建 DuckDB 数据源 → 点「上传库文件」选 accept.duckdb
→ 弹窗内「库文件路径」自动回填：
   5[text]=/data/workspace/monitor-deploy/data/file-db/9445663a1e52-accept.duckdb
→ 填名称 accept-ui3 → 保存 → 后端台账出现 id=16 accept-ui3 (duckdb)
→ 打开管理台 → 左侧见 upload_demo 表（上传文件自带），列 INTEGER/VARCHAR/DOUBLE，数据 2 行
```

> 注意：验收中复现并修复了一个真实缺陷 —— **DuckDB 保存被后端端口校验误拒**。
> 前端 DuckDB 表单无主机/端口，`port` 占位为 0；后端 `DatasourceServiceImpl.validate()` 的
> 「端口需为 1~65535」把 0 拒了，导致「上传→保存」建不出源。
> 修复：文件库类型（`isFileJdbcType`）跳过端口范围校验。修复后带 `port=0` 建 DuckDB 返回 00000（id=15）。
> 已补 `DatasourceCreateValidateTest` 4 用例钉住。

### ② DuckDB 设计表类型下拉 = DuckDB 方言组

```text
管理台打开 accept-ui3 → 新建表（设计表）→ 类型下拉：
  TINYINT,SMALLINT,INTEGER,BIGINT,HUGEINT,UTINYINT,USMALLINT,UINTEGER,UBIGINT,
  BOOLEAN,DECIMAL,REAL,DOUBLE,VARCHAR,TEXT,BLOB,DATE,TIME,TIMESTAMP,
  TIMESTAMPTZ,INTERVAL,JSON,UUID
（22 项，均为 DuckDB 内核认的类型：HUGEINT/UTINYINT/USMALLINT/UINTEGER/UBIGINT/
  TIMESTAMPTZ/INTERVAL/UUID 是 DuckDB/Postgres 系特有；无 MySQL 的 ENUM/SET/GEOMETRY）
```

### ③ MySQL 8.4 设计表选 VECTOR → 报错来自内核而非白名单

```text
MySQL 数据源（instabox-e2e, id=3）设计表 → 类型下拉 41 项含 VECTOR
（TINYINT…JSON,VECTOR，MySQL 方言组；无 DuckDB 特有类型）
→ 选 VECTOR、填维度、点创建 → 弹窗内原样展示内核错误：

  新建表失败: You have an error in your SQL syntax; check the manual that
  corresponds to your MySQL server version for the right syntax to use near
  'VECTOR(255) NULL)' at line 1

后端日志同条铁证：
  java.sql.SQLSyntaxErrorException: …near 'VECTOR(255) NULL)' at line 1

即：VECTOR 已过 DbColumnDef 白名单放行，报错来自 MySQL 8.4 内核（8.4 无此类型，
MySQL 9 / pgvector 才支持）；前端按设计原样透出（CreateTableDialog 错误区）。
```

---

## 五、部署方法（本轮实测）

本轮采用 **lib 分离替换**（只发 datasource-hub 一个 jar，不发全部）：

```bash
# 1) lib/ 里替换 jar（带 immutable 属性需先 chattr -i）
sudo chattr -i lib/spring-support-datasource-hub-starter-4.0.0.42.jar
scp spring-support-datasource-hub-starter-4.0.0.42.jar …:/data/workspace/monitor-deploy/lib/
sudo chattr +i lib/spring-support-datasource-hub-starter-4.0.0.42.jar

# 2) 清 app/ 遮蔽层（app 在 classpath 前会遮蔽 lib 同全限定名类）
rm -rf app/com/chua/starter/datasourcehub        # ← 关键！否则加载旧类（曾出现 NoSuchMethodError）

# 3) 重启
./deploy.sh restart
```

> **遮蔽层教训（本轮实测踩坑）**：lib jar 已是新构建（javap 确认含 `getFileDbMaxBytes()`），
> 但 app/ 里残留 38 个旧 class（含旧版 `DatasourceHubProperties`，无该方法），
> classpath `app:` 在 `lib:` 前 → 运行时 `NoSuchMethodError: getFileDbMaxBytes()`，
> /file-db/upload 与 /ms/monitor/db/tables 全 500。删除 app 遮蔽层后立即恢复。
> 另注意：并行部署其他模块（如 filesystem-starter）的流程可能把 app/ 遮蔽层重新铺回并抢占 vite 端口，
> 验收前需确认 8848 与后端均为本机目标。

前端**无需构建发布**：改动只在源码中，用户 dev server（8848）热更即可；正式发布由用户侧处理。

---

## 六、备注

- 本机临时验收环境在 `D:\ch\project\.tmp\localapp`（含解包产物约 1.2GB 与测试用库文件），后端已停止、端口 8082/8856 已释放，可自行删除。
- 上传大小上限可用 `plugin.datasource-hub.file-db-max-bytes` 调整；落盘目录用 `plugin.datasource-hub.file-db-upload-dir`。
- PostgreSQL / Oracle / SQL Server 的统计 SQL（上一轮方言化）仍无实例可联调，未在本轮复测范围。