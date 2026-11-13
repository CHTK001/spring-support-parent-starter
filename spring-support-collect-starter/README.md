# spring-support-collect-starter

采集平台后端：数据源、目录、字段、变量、函数 → 流程与版本 → 任务、执行记录、告警规则与通知。排期能力收口在 `CollectFlowSchedulePort`，有 job 台账时落 `sys_job`，没有时本地兜底。

> 版本 `4.0.0.51`；父 POM 继承 `spring-boot-starter-parent:4.1.0`，Java 25。

## 一、能力概览

| 能力 | 入口 |
| --- | --- |
| 自动装配 | `CollectAutoConfiguration`、`CollectScheduleConfiguration`（`AutoConfiguration.imports`） |
| 控制器 | 14 个（`CollectAlertRuleController`, `CollectCategoryController`, `CollectDatasourceController`, `CollectDesignerController`, `CollectExecutionController`, `CollectFieldController`） |
| Service | 14 个 |
| 配置前缀 | `plugin.collect.*` |
| 建表脚本 | 3 个 SQL 文件 |

## 二、引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-collect-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

内部依赖：`mybatis`、`datasource`、`datasource-hub`、`job`

## 三、装配与开关

`CollectAutoConfiguration`（`plugin.collect.enable` 默认 true）负责组件/ Mapper 扫描与建表脚本；`CollectScheduleConfiguration` 按容器里有没有 `JobOperator` 选择排期实现。

## 四、公开 Bean

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `collectFlowSchedulePort` | `CollectFlowSchedulePort` | 排期端口：有 job 台账用台账实现，没有则本地兜底（定时不可用）。 |
| `yamlMimeMappings` | `WebServerFactoryCustomizer<TomcatServletWebServerFactory>` | 追加 yaml 相关的 MIME 映射。 基于 MimeMappings#DEFAULT 复制一份再追加，避免覆盖 Tomcat 自带的映射 （例如 .wasm 已经是 application/wasm，丢掉会连带影响 WebAssembly 加载）。 |

## 五、Service（组件扫描，可直接注入）

| Service | 说明 |
| --- | --- |
| `CollectAlertRuleServiceImpl` | 采集告警规则服务实现。 |
| `CollectCategoryServiceImpl` | 采集分类服务实现。 |
| `CollectDatasourceServiceImpl` | 采集数据源服务实现。 |
| `CollectDesignerServiceImpl` | 设计器与运行时概览服务实现。 |
| `CollectExecutionServiceImpl` | 采集执行服务实现。 |
| `CollectFieldServiceImpl` | 采集结果字段服务实现。 |
| `CollectFlowServiceImpl` | 采集流程服务实现。 调度不再由本模块轮询：每个流程在调度台账（sys_job）里有一行自己的 cron （键 collect-flow-），启停/改 cron/删除都会同步那一行，到点由 job-starter 直接派发。与调度器的交互全部收口在 CollectFlowSche… |
| `CollectFlowVersionServiceImpl` | 流程版本服务实现。 |
| `CollectFunctionServiceImpl` | 采集自定义函数服务实现。 同时是引擎侧的 CollectFunctionProvider：注册表按名字来这儿取闭包源码， 本类只负责把启用的行交出去。写入前一定先编译，编译不过不给存，避免把坏函数放进运行链。 |
| `CollectNoticeServiceImpl` | 采集执行通知服务实现。 渠道表在构造期由 Spring Bean 与 @Spi 扩展共同填充，Spring 侧优先； 发送统一走单个后台线程，因此邮件或回调卡住都不会拖住任务收尾，也不会与流程线程池抢名额。 |
| `CollectRecordServiceImpl` | 采集结果服务实现。 |
| `CollectSettingsServiceImpl` | 采集平台运行参数服务实现。 定义表是唯一的可信来源：键、类型、范围、是否重启都在这里声明， 前端只按接口返回的元数据渲染，不在前端硬编码任何一项。缺省值在构造时从 CollectProperties 抓一份快照，之后 yml 值不会被运行期修改污染。 |
| `CollectTaskServiceImpl` | 采集任务查询服务实现。 |
| `CollectVariableServiceImpl` | 采集全局变量服务实现。 |

## 六、HTTP 接口

### `CollectAlertRuleController`（`/v2/collect/alert-rule`）

采集告警规则接口。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/collect/alert-rule/page` | `page` |  |
| GET | `/v2/collect/alert-rule/list` | `listEnabled` |  |
| POST | `/v2/collect/alert-rule/save` | `save` |  |
| DELETE | `/v2/collect/alert-rule/delete` | `delete` |  |
| POST | `/v2/collect/alert-rule/toggle` | `toggle` |  |

### `CollectCategoryController`（`/v2/collect/category`）

采集分类接口。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/collect/category/tree` | `tree` | 分类树。 |
| GET | `/v2/collect/category/options` | `options` | 分类下拉选项。 |
| POST | `/v2/collect/category/save` | `save` | 新增分类。 |
| PUT | `/v2/collect/category/update` | `update` | 修改分类。 |
| DELETE | `/v2/collect/category/delete` | `delete` | 删除分类，存在子分类或流程时拒绝。 |

### `CollectDatasourceController`（`/v2/collect/datasource`）

采集数据源接口。 列表与详情永不回传密码，只回是否已设置；连接测试允许用未保存的配置。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/collect/datasource/list` | `list` | 数据源列表。 |
| GET | `/v2/collect/datasource/options` | `options` | 数据源下拉选项，首项为应用主库。 |
| POST | `/v2/collect/datasource/save` | `save` | 新增数据源。 |
| PUT | `/v2/collect/datasource/update` | `update` | 修改数据源，密码留掩码表示不改。 |
| DELETE | `/v2/collect/datasource/delete` | `delete` | 删除数据源，被流程引用时拒绝。 |
| POST | `/v2/collect/datasource/test` | `test` | 测试连接，返回数据库产品信息。 |
| GET | `/v2/collect/datasource/tables` | `tables` | 列出数据源里的表与视图，供入库节点选择目标表。 |
| GET | `/v2/collect/datasource/columns` | `columns` | 列出指定表的列元数据，供入库节点配置字段映射。 |

### `CollectDesignerController`（`/v2/collect/designer`）

采集设计器接口。 工具箱、属性面板、运行概览与定时预览的数据都来自这里，设计器不内置节点类型清单。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/collect/designer/metas` | `metas` | 全部节点类型元数据。 |
| GET | `/v2/collect/designer/agents` | `agents` | 已注册 Agent 目录，供设计器刷新 Agent 节点下拉。 |
| GET | `/v2/collect/designer/runtime` | `runtime` | 运行时概览。 |
| GET | `/v2/collect/designer/cron/next` | `cronNext` | 预览定时表达式的触发时间。 |
| GET | `/v2/collect/designer/fetch-preview` | `fetchPreview` | 预览抓取：把 URL 抓回来给「元素拾取器」渲染与点选。 设计期拿不到运行期的抓取变量，所以单开这个只读出口。仅允许 http/https， 且拒绝内网与本机地址（见 CollectDesignerServiceImp… |
| POST | `/v2/collect/designer/ai-generate` | `aiGenerate` | AI 生成流程：把自然语言描述生成流程 DSL（经图校验后返回）。 |

### `CollectExecutionController`（`/v2/collect/execution`）

采集执行接口。 运行请求提交后立即返回任务号，执行在虚拟线程里进行；试运行不落结果，只回任务与节点日志。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| POST | `/v2/collect/execution/run` | `run` | 运行已保存的流程。 |
| POST | `/v2/collect/execution/dry-run` | `dryRun` | 试运行未保存的流程图，结果不落库。 |
| POST | `/v2/collect/execution/stop` | `stop` | 停止运行中的任务。 |
| GET | `/v2/collect/execution/running` | `running` | 本机执行中的任务数。 |
| GET | `/v2/collect/execution/inflight` | `inflight` | 某流程排队与执行中的任务数。 |

### `CollectFieldController`（`/v2/collect/field`）

采集结果字段接口。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/collect/field/list` | `list` | 某流程节点的字段定义。 |
| POST | `/v2/collect/field/replace` | `replace` | 整体保存字段定义。 |

### `CollectFlowController`（`/v2/collect/flow`）

采集流程接口。 流程定义即前端设计器保存的 DSL，列表不返回 DSL 正文，只有详情返回。 校验失败由全局异常处理转成 400 与中文提示，控制器不再重复包装。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/collect/flow/page` | `page` | 流程分页列表。 |
| GET | `/v2/collect/flow/list` | `list` | 流程精简列表。 |
| GET | `/v2/collect/flow/detail` | `detail` | 流程详情，含流程图 DSL。 |
| POST | `/v2/collect/flow/save` | `save` | 新增流程。 |
| PUT | `/v2/collect/flow/update` | `update` | 修改流程。 |
| PUT | `/v2/collect/flow/status` | `status` | 启停流程调度。 |
| POST | `/v2/collect/flow/copy` | `copy` | 复制流程。 |
| DELETE | `/v2/collect/flow/delete` | `delete` | 删除流程。 |
| POST | `/v2/collect/flow/validate` | `validate` | 只校验流程图不落库，供设计器保存前检查。 |

### `CollectFlowVersionController`（`/v2/collect/flow-version`）

流程版本接口。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/collect/flow-version/list` | `list` |  |
| POST | `/v2/collect/flow-version/save` | `save` |  |
| POST | `/v2/collect/flow-version/rollback` | `rollback` |  |
| GET | `/v2/collect/flow-version/diff` | `diff` |  |

### `CollectFunctionController`（`/v2/collect/function`）

自定义函数接口。 函数登记后进的是编译校验这一关，存下来即可在脚本里 fn.名字(...) 调用， 也能在任意节点参数里写 ${名字(...)}，两条路共用同一张函数表。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/collect/function/list` | `list` | 函数列表。 |
| POST | `/v2/collect/function/save` | `save` | 新增函数。 |
| PUT | `/v2/collect/function/update` | `update` | 修改函数。 |
| DELETE | `/v2/collect/function/delete` | `delete` | 删除函数。 |
| POST | `/v2/collect/function/test` | `test` | 试跑函数。 |

### `CollectNoticeController`（`/v2/collect/notice`）

执行通知接口。 通知是平台级配置，库里没有流程维度，每条配置对每次执行都参与命中； 渠道清单来自注册表，第三方注册新的 CollectNotifyChannel 后这里与前端下拉都会自动出现。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/collect/notice/list` | `list` | 通知配置列表，全平台一份。 |
| GET | `/v2/collect/notice/channels` | `channels` | 可用渠道及其接收目标填法。 |
| POST | `/v2/collect/notice/save` | `save` | 新增通知配置。 |
| PUT | `/v2/collect/notice/update` | `update` | 修改通知配置。 |
| DELETE | `/v2/collect/notice/delete` | `delete` | 删除通知配置。 |
| POST | `/v2/collect/notice/test` | `test` | 按配置试发一条通知。 |

### `CollectRecordController`（`/v2/collect/record`）

采集结果接口。 记录内容以有序字段列表返回，前端按字段定义渲染表格。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/collect/record/page` | `page` | 结果分页列表。 |
| GET | `/v2/collect/record/detail` | `detail` | 结果详情。 |
| DELETE | `/v2/collect/record/delete` | `delete` | 删除单条结果。 |
| POST | `/v2/collect/record/deletes` | `deletes` | 批量删除结果。 |
| DELETE | `/v2/collect/record/clear` | `clear` | 清空结果，流程与任务至少指定一个。 |
| GET | `/v2/collect/record/count` | `count` | 结果总数。 |

### `CollectSettingsController`（`/v2/collect/settings`）

采集平台运行参数接口。 把 yml 里的 plugin.collect.* 暴露成可读可写的配置项：GET 返回全部定义与当前值， PUT 保存并立即生效，POST /reset 恢复 yml 缺省。写值失败由全局异常处理转成 400， 控制器不重复包装。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/collect/settings/list` | `list` | 全部运行参数（含类型、范围、缺省值与是否重启生效）。 |
| PUT | `/v2/collect/settings` | `save` | 保存运行参数，只传要改的键；校验通过后写库并立即生效。 |
| POST | `/v2/collect/settings/reset` | `reset` | 恢复缺省：传键则只恢复这些键，不传或传空数组则恢复全部。 |

### `CollectTaskController`（`/v2/collect/task`）

采集任务接口。 任务是流程的一次执行，节点日志按执行顺序返回，节点状态用于设计器回显每个节点的最近结果。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/collect/task/page` | `page` |  |
| GET | `/v2/collect/task/detail` | `detail` |  |
| GET | `/v2/collect/task/logs` | `logs` |  |
| GET | `/v2/collect/task/node-states` | `nodeStates` |  |
| DELETE | `/v2/collect/task/delete` | `delete` |  |
| POST | `/v2/collect/task/clean` | `clean` |  |
| GET | `/v2/collect/task/quality` | `quality` | 任务质量报告：从节点日志聚合质量指标 |
| GET | `/v2/collect/task/stats` | `stats` | 流程执行历史统计（趋势 + 节点耗时） |
| GET | `/v2/collect/task/live` | `live` | 运行中任务的实时节点状态（供前端轮询） |

### `CollectVariableController`（`/v2/collect/variable`）

全局变量接口。 加密变量只回掩码，回传掩码表示不修改原值。

| 方法 | 路径 | Java 方法 | 说明 |
| --- | --- | --- | --- |
| GET | `/v2/collect/variable/list` | `list` | 变量列表，密文值脱敏。 |
| POST | `/v2/collect/variable/save` | `save` | 新增变量。 |
| PUT | `/v2/collect/variable/update` | `update` | 修改变量。 |
| DELETE | `/v2/collect/variable/delete` | `delete` | 删除变量。 |

## 七、配置

### `plugin.collect.*`（`CollectProperties`）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enable` | `boolean` | `true` | 平台总开关，关闭后不装配调度任务与执行引擎。 |
| `default-thread-count` | `int` | `4` | 流程默认并行线程数，可被流程定义覆盖。 |
| `request-timeout-ms` | `int` | `30000` |  |
| `request-retry-times` | `int` | `1` | 默认重试次数，节点未显式配置时使用。 |
| `user-agent` | `String` | `DEFAULT_USER_AGENT` | 默认 User-Agent，节点未显式配置时使用。 |
| `sql-allow-write` | `boolean` | `false` | SQL 写操作开关：采集约定强暗示，默认必须禁止写入，关闭时才允许。 */ |
| `task-timeout-ms` | `long` | `1800000L` |  |
| `max-record-per-task` | `int` | `100000` |  |

## 八、数据表与脚本

| 脚本 | 建表数 | 表名（前 12 个） |
| --- | --- | --- |
| `db/init/V1.0__init_all_api_collect.sql` | 12 | `collect_category`, `collect_flow`, `collect_datasource`, `collect_variable`, `collect_task`, `collect_task_log`, `collect_field`, `collect_record`, `collect_notice`, `collect_function`, `collect_alert_rule`, `collect_flow_version` |
| `db/init/V1.0__initdata_all_api_collect.sql` | 0 | — |
| `db/init/V1.1__add_collect_setting.sql` | 1 | `collect_setting` |

## 九、通用场景

先登记 `datasource` 与 `category` / `field`，再编排 `flow` 并发布版本；运行时生成 `task` 与 `execution` 记录，告警规则独立配置，通知走 `CollectNoticeController`。

## 十、注意事项

- 采集本体不依赖 `job-starter`：排期只认 `CollectFlowSchedulePort`，换调度器不用改流程。
- `plugin.collect.sql-allow-write` 默认 false，采集默认只读；要写库必须显式打开。
- 流程版本发布后运行期只认已发布版本，设计器改动不会直接影响线上任务。

## 十一、验证

```bash
mvn -pl spring-support-collect-starter -am -DskipTests package
```

接口路径以本文档为准，启动后用 `GET /service-info` 查看模块开关与端点汇总。
