# 采集平台（collect）后端接口清单

> 模块：`com.chua:spring-support-collect-starter`（`spring-support-parent-starter` reactor 内，artifactId 已去掉 `api-`）
> 扫描来源：`src/main/java/com/chua/starter/collect`
> 平台层来源：`D:/ch/project/spring-support-parent-starter`（`Query` / `PageResultUtils` / `ReturnPageResult` / `RequestParamMapping` / `ApiUniformResponseBodyAdvice` / `ApiExceptionAdvice`）、
> `D:/ch/project/utils-support-parent-starter/utils-support-core-parent/utils-support-common-starter`（`ReturnResult` / `ReturnCode` / `PageResult`）
> 字段名均为逐字照抄代码。
>
> **校订记录**
>
> | 日期 | 内容 |
> |---|---|
> | 2026-09-29 | 首次生成（扫描源码） |
> | 2026-09-26 | 校订：删除已废弃的 `CollectConsoleAuthController` / `AuthGate` / Session 鉴权章节（改为框架 oauth static 协议）；补齐 `Function` / `Notice` / `SchedulerConsole` 三个控制器章节；端点汇总由「10 控制器 / 49 handler」更正为 **12 控制器 / 69 handler**；shape 总数由 14 更正为 **13**。本文档从 `.tmp/` 迁入模块 `docs/`，避免随临时目录清理丢失。 |
> | 2026-09-26 | 修正 `exceptionFlow` 对任务终态的影响：只有 `"1"`（显式异常分支）算「失败已被接住」，默认 `"0"` 不再把节点失败静默吞成 `SUCCESS`。代码见 `CollectEngine.dispatch` 与 `CollectEdge.isExceptionBranch`。 |
>
> 校订依据为对运行实例的实测（`18191` SCHEDULER / `18192` EXECUTOR）与 `application.yml`、`CollectProperties`、`CollectSchedulerConsoleController` 源码的逐项比对。

## 0. 全局约定

### 0.1 Base URL

| 项 | 值 | 出处 |
|---|---|---|
| 服务端口 | `18190` | `resources/application.yml` |
| context-path | `/collect/api` | `server.servlet.context-path` |
| 接口前缀 | `/v2/collect/**` | 各类 `@RequestMapping` |
| 鉴权前缀 | `/collect-console/auth/**` | `CollectConsoleAuthController` |

所有路径在下表中都是**不含 context-path** 的路径；真实请求 URL = `http://host:18190/collect/api` + 表中路径。
本模块**只提供接口**：内置静态控制台与页面跳转控制器已删除，前端是独立 Vue 应用（见 §0.6）。

### 0.2 响应包装（重要）

`ApiUniformResponseBodyAdvice` 的逻辑：返回值已经是 `com.chua.common.support.lang.code.ReturnResult` 时**不再包装**；
否则统一 `ReturnResult.success(o)`。

- **非分页接口**（控制器直接返回 `ReturnResult`）→ 单层：
  ```json
  { "code": "00000", "msg": "操作成功", "data": <payload>, "ok": true, "success": true }
  ```
  `code` 是**字符串**（`ReturnResult.code` 为 `String`），成功值是 `"00000"`。
  `ok` / `success` 由 `isOk()` / `isSuccess()` 派生，始终出现。`data` 为 `null` 时字段仍存在。
  `temp` 字段是 `transient`，不会序列化。

- **分页接口**（控制器返回 `com.chua.starter.common.support.lang.ReturnPageResult`）→ **会被再包一层**。
  advice 里的 `instanceof` 判的是另一个类 `com.chua.common.support.lang.code.ReturnPageResult`（不同包、只有 `data` 字段），
  与控制器用的 `starter.common.support.lang.ReturnPageResult` 不是同一个类型，`isPageResultType()` 的后缀判断（`result.PageResult`）也匹配不上，
  于是分页结果被 `ReturnResult.success(...)` 包住。**双层**。

- 失败：`code` 为字符串错误码，`data` 为 `null`，HTTP 状态码见 §0.7。

前端判定成功的推荐写法：`['00000','200',0,'0'].includes(res.code)`（内置控制台 `console.js` 就是这么写的）。

### 0.3 分页请求参数与别名

`Query<T>`（`com.chua.starter.mybatis.entity.Query`）由 `RequestParamsMapMethodArgumentResolver` 绑定，
字段上的 `@RequestParamMapping` 提供查询参数**别名**（按数组顺序取第一个命中的）：

| Java 字段 | 类型 | 默认值 | 可用查询参数名（别名，任一即可） |
|---|---|---|---|
| `page` | Integer | `1` | `page`、`pageNo`、`pageNumber`、`pageNum`、`current` |
| `pageSize` | Integer | `10` | `pageSize`、`size`、`count` |
| `prop` | String[] | `[]` | `prop`、`field`（逗号分隔的选取字段） |
| `order` | String | `null` | `order`、`sortBy`、`sort`、`orderBy` |

- 页码从 1 开始；`pageSize` 上限 100（`@Max(100)` 仅作 Bean Validation 声明，控制器参数上没有 `@Valid`，**实际不会被校验**）。
- `order` 的写法：`"列名 desc"` / `"列名 asc"`，多个逗号分隔（`createFullPage` 才解析成 `OrderItem`）。
  **注意：三个分页控制器都调用 `query.createPage()`，不是 `createFullPage()`，所以 `order` / `prop` 对采集模块的分页接口无效**（排序固定由各 Service 写死，都是按主键倒序/名称升序）。

### 0.4 查询条件对象（`@ParameterObject` 实体）

`page` 接口的第二个参数（`CollectFlow` / `CollectTask` / `CollectRecord`）没有 `@RequestParam`，走 Spring 默认 `@ModelAttribute` 绑定，
**查询参数名 = Java 字段名**（不是数据库列名），且都是可选。各接口真正生效的条件字段见对应小节。

### 0.5 分页响应结构（真实返回）

`com.chua.starter.common.support.lang.ReturnPageResult<T>`：`{code:int, msg:String, data:PageResult<T>}`
（`com.chua.common.support.lang.code.PageResult<T>`：`{pageNo:int, pageSize:int, totalPages:int, total:long, data:T[]}`）

再叠加 §0.2 的双层包装，**最终 JSON**：

```json
{
  "code": "00000",
  "msg": "操作成功",
  "ok": true,
  "success": true,
  "data": {
    "code": 0,
    "msg": "success",
    "data": {
      "pageNo": 1,
      "pageSize": 20,
      "totalPages": 3,
      "total": 42,
      "data": [ /* CollectFlowVO ... */ ]
    }
  }
}
```

注意内层 `code` 是 **int `0`**、`msg` 是 `"success"`；外层 `code` 是 **字符串 `"00000"`**。
取值路径：`resp.data.data.data` = 列表，`resp.data.data.total` = 总数。
空分页时 `data.data.data` 为 `null`（`PageResult.empty()` 是 `builder().build()`，字段为默认值、`data` 为 null），务必兜底。

### 0.6 鉴权

> **2026-09-26 校订**：本节原文描述的是一套**已废弃**的自建控制台会话方案
> （`CollectConsoleAuthController` + `CollectConsoleAuthInterceptor` + `CollectConsoleWebMvcConfigurer` +
> Servlet Session `JSESSIONID` + 前端 `AuthGate` 浮层 + `plugin.collect.console.*` 配置）。
> 这些类与配置在当前代码中**均不存在**（`CollectProperties` 无 `console` 子节点，`configuration` 包为空目录）。
> 实际实现改为复用框架 oauth 的 `static` 协议，以下一行为准。

本模块**不自带鉴权**。`/v2/collect/**` 与 `/v2/scheduler/**` 全部由平台统一认证过滤器保护，
凭据来自框架登录接口 `POST /v2/user/login` 签发的令牌。

| 项 | 值（摘自 `application.yml`） |
|---|---|
| 认证开关 | `plugin.oauth.enable: true` |
| 协议 | `plugin.oauth.protocol: static`（本地校验 `sys_user` 并用 SM4 自签令牌，不依赖外部认证服务器） |
| 自身前缀 | `plugin.oauth.address` / `plugin.oauth.auth-address`: `http://127.0.0.1:18190/collect/api` |
| 未认证回跳 | `plugin.oauth.login-address: /#/login`（前端是独立 SPA，指向其 hash 登录路由） |
| 令牌头名 | `plugin.oauth.token-name: x-oauth-token`（前端另按框架惯例发 `Authorization: Bearer <token>`） |
| 令牌有效期 | `plugin.oauth.static-token-expire-seconds: 7200` |
| 白名单 | `use-default-whitelist: false`，显式只放行下面 8 条 |
| 模块总开关 | `plugin.collect.enable`（默认 true；`false` 时所有 collect Bean 不装配 → 接口 404） |

白名单（`plugin.oauth.whitelist`）：

```text
/v2/user/login
/v2/user/logout
/v2/user/refresh-token
/**/captcha
/v3/api-docs/**
/doc.html
/webjars/**
/actuator/**
```

**未登录时**（实测于 `18191`）：

- **HTTP 403**，响应体 `{"code":..., "msg":"用户信息不存在/请重新登录!", ...}`。
  注意是 **403 而非 401**——这是平台认证过滤器的口径，早期版本文档里的 `401 + {"code":"401"}` 已不适用。
- 前端 `src/api/http.ts` 的会话失效分支按 **401** 判定并跳框架登录页；
  由于后端回 403，实际表现是走 `!response.ok` 分支抛 `ConsoleError` 并弹出后端给的中文 `msg`。
  登录态是否失效以框架路由守卫（`getAccessToken()` 为空即跳 `/login`）为准，**不要依赖后端 401**。

种子账号（`V1.0__initdata_all_api_common_system.sql`）：`sa` / `admin` / `ops`，默认密码 `admin123`，
登录地址 `POST /collect/api/v2/user/login`，请求体 `{"userName":"...","password":"..."}`。

### 0.7 错误语义（业务异常 → HTTP 状态码 + body）

`ApiExceptionAdvice`（`@RestControllerAdvice`）统一处理，业务代码抛异常即可：

| 抛出 | HTTP | body 的 `code` | body 的 `msg` |
|---|---|---|---|
| `IllegalArgumentException` | **400** | `C0400S0000`（含 "must not be null"/"cannot be null" 时 `C0400S0001`） | 异常 message 原文（中文，可直接展示） |
| `IllegalStateException` | **409** | `B0500S0001` | 异常 message 原文 |
| `MissingServletRequestParameterException` | 400 | `C0400S0001` | 缺参提示 |
| JSON 解析失败 / 类型不匹配 | 400 | `C0400S0000` / `C0400S0001` | `"JSON格式错误…"` 等 |
| 404（无 handler / 无资源） | 404 | `RESOURCE_NOT_FOUND` | `"请求的接口不存在…"` |
| 其他未捕获 | 500 | `SYSTEM_EXECUTION_ERROR` | 通用文案 |

- 失败响应形状：`{"code":"C0400S0000","msg":"流程不存在或已删除: 12","data":null,"ok":false,"success":false}`，HTTP 400。
- **例外**：框架登录接口 `POST /v2/user/login` 的账号密码错误**不抛异常**，直接返回业务错误信封 →
  **HTTP 200** + `ok:false`。实测（`sa` / 错误密码）响应体：
  ```json
  { "code": "C0400S0004", "msg": "账号和密码不正确", "ok": false, "success": false }
  ```
  登录是否成功必须看 `code` / `ok`，不能只看 HTTP 状态。
- **未认证**不是异常路径：平台认证过滤器在进入 MVC 之前就返回 **403**
  （`msg` 形如 `用户信息不存在/请重新登录!`），不会走到 `ApiExceptionAdvice`。

### 0.8 枚举与前端下拉常量

**任务状态** `collectTaskStatus`（`support/CollectTaskStatus`，字符串常量）：
`RUNNING` 执行中 / `SUCCESS` 成功 / `FAILURE` 失败 / `STOPPED` 手动停止 / `SKIPPED` 未执行（仅出现在节点日志）。

**节点日志状态** `collectTaskLogStatus`：`SUCCESS` / `FAILURE` / `SKIPPED`。

**触发方式** `collectTaskTriggerType`（`support/CollectTriggerType`）：
`CRON` 定时调度 / `MANUAL` 手动运行（结果落库）/ `TEST` 试运行（结果不落库）。

**流程调度状态** `collectFlowStatus`：`0` 停用 / `1` 启用（Integer）。

**数据源状态** `collectDatasourceStatus`：`0` 禁用 / `1` 启用；`collectDatasourceType`：`MYSQL` / `H2` / `POSTGRESQL`。
数据源引用名保留字：`app`（= 应用主库，`CollectJdbcSupport.APP_DATASOURCE`）。

**字段类型** `collectFieldType`：`STRING` / `INT` / `LONG` / `DECIMAL` / `DATE` / `TEXT`（默认 `STRING`）。
`collectFieldPrimary`：`1` 业务主键（同键覆盖）/ `0` 普通；`collectFieldEnabled`：`1` 显示 / `0` 隐藏。
自动登记字段上限 100（`CollectFieldServiceImpl.MAX_AUTO_FIELDS`）。

**抽取字段 `parserType`**：`CSS`（默认）/ `REGEX` / `JSON` / `TEXT` / `HTML`（大小写不敏感，服务端会 `toUpperCase`；未知值按 `CSS` 处理）。
**抽取字段 `attribute`（取值方式）**：`text`（默认）/ `attr` / `html` / `outer`（大小写不敏感，`toLowerCase`）。

**连线异常流向 `exceptionFlow`**（字符串，`engine/model/CollectEdge`，与 spider-flow 对齐）：
`"0"` 总是流转（默认，空串等同 `"0"`）/ `"1"` 仅上游异常时流转（异常分支）/ `"2"` 仅上游正常时流转。

> **2026-09-26 修正**：三个取值对**流转**的含义不变，但对**任务终态**的影响此前与文档不符。
> 节点失败是否算「被接住」现在只看有没有 `"1"`（显式异常分支），`"0"` 不再算接住。
> 原因：`"0"` 只是「无论成败都往下走」的路由声明，不代表下游承诺兜住异常；
> 把它算作接住会让节点失败被静默吞掉——任务终态恒为 `SUCCESS`，
> 且以 `FAILURE` 为条件的执行通知（`support/CollectNotifyTiming`）永远不会投递。
>
> | 配置 | 节点 | 任务终态 |
> |---|---|---|
> | `"0"`（默认） | 失败节点 `FAILURE`，下游仍继续 | **`FAILURE`**，`collectTaskError` 带失败原因 |
> | `"1"` 异常分支 | 失败节点 `FAILURE`，下游接异常 | `SUCCESS`（失败已被显式处理） |
> | `"2"` 仅正常 | 失败节点 `FAILURE`，下游 `SKIPPED` | **`FAILURE`** |

**全局变量**：`collectVariableEncrypted` `0` 明文 / `1` 密文；变量名规则 `[A-Za-z_][A-Za-z0-9_]{0,63}`。
**密文掩码**：读回时 `collectVariableValue` 为 `"******"`（`CollectSecrets.MASK`）；把掩码原样提交表示不修改。数据源密码**永不下发**（VO 只有 `passwordSet`）。

### 0.9 节点 shape 全量清单（`/v2/collect/designer/metas` 的返回来源）

> **2026-09-26 校订**：原文标题写「共 14 个」，实测 `GET /v2/collect/designer/metas` 返回
> **13 个**。原文把 `function` 也算成了节点 shape，但它不是节点——`function` 是自定义函数库
> （`/v2/collect/function/**`），没有对应执行器，不会出现在 `metas` 里。
>
> **2026-09-28 补 `database`**：新增「数据库」节点后全量为 **14 个**，下表 14 行即全量。

shape 值即 `nodes[].shape`；分组 `group` 只有 4 个：`来源`、`解析`、`控制`、`输出`。

| shape | label | group | icon | 说明 |
|---|---|---|---|---|
| `start` | 开始 | 来源 | `play` | 声明种子 URL |
| `request` | 抓取 | 来源 | `globe` | 请求网页/接口，产出 `resp`/`respDoc` |
| `link` | 链接 | 解析 | `link` | 从页面抽链接集合 |
| `extract` | 抽取 | 解析 | `filter` | CSS/正则/JSON 抽取字段 |
| `sql` | SQL | 输出 | `database` | 读写数据库 |
| `output` | 采集平台 | 输出 | `database` | 落采集平台自身结果表，可镜像业务表 |
| `database` | 数据库 | 输出 | `database` | 整批写库，不产采集记录；表不存在自动建 |
| `variable` | 变量 | 控制 | `sigma` | 表达式赋值 |
| `dedup` | 去重 | 控制 | `shield` | 按业务主键跳过已采 |
| `sleep` | 等待 | 控制 | `clock` | 毫秒等待/限速 |
| `comment` | 注释 | 控制 | `note` | 无副作用 |
| `script` | 脚本 | 解析 | `code` | Groovy 脚本处理变量 |
| `process` | 子流程 | 控制 | `share` | 调用另一条流程 |
| `end` | 结束 | 输出 | `stop` | 流程终点，无副作用 |

> **结构校验（09-21 起）**：`CollectGraph.checkStartEnd()` 要求**有且仅有一个 `start`**，且**每条分支的末端必须是 `end`**（`end` 不能有出边）。保存、`/flow/validate` 与运行期 `CollectEngine` 走同一条校验。

**所有节点都带 4 个循环参数**（`CollectShapeMetas.loopParams()`，追加在 `params` 数组开头）：

| 参数 name | label | type | defaultValue | tip |
|---|---|---|---|---|
| `loopCount` | 循环集合/次数 | `textarea` | — | 填次数如 10，或集合表达式如 `${urlList}`、`${rows}`；留空只执行一次 |
| `loopVariableName` | 下标变量名 | `text` | `index` | 循环下标写入的变量名，留空用 index |
| `loopItemVariableName` | 元素变量名 | `text` | `item` | 当前迭代元素写入的变量名，下游用 `${item}` 引用 |
| `loopParallel` | 迭代并行 | `select`（options `["0","1"]`） | `0` | 选 1 时各迭代同时入池，由流程线程数限流 |

> 命名坑：label 叫"循环集合/次数"的字段名是 `loopCount`，label 叫"下标变量名"的是 `loopVariableName`，
> label 叫"元素变量名"的是 `loopItemVariableName`（Java 常量 `PARAM_LOOP_INDEX` / `PARAM_LOOP_ITEM_VARIABLE` 与字面量不一致，以前端 JSON 字面量为准）。

各节点自有参数（按 `params` 数组中出现的顺序，均在循环参数之后）：

**`start`**
| name | label | type | required | options / 默认 |
|---|---|---|---|---|
| `urls` | 种子 URL | `textarea` | ✔ | 每行一个 URL，可用 `${变量}` 拼接 |
| `outputVariable` | 集合变量名 | `text` | | 默认写入 `urlList` |

**`request`**
| name | label | type | required | options / 默认 |
|---|---|---|---|---|
| `url` | URL | `text` | ✔ | 支持 `${}` |
| `method` | 请求方法 | `select` | | `GET`/`POST`/`PUT`/`DELETE`，默认 `GET` |
| `body` | 请求体 | `textarea` | | |
| `contentType` | 请求体类型 | `text` | | 有 body 且留空时自动 `application/x-www-form-urlencoded;charset=UTF-8` |
| `timeout` | 超时毫秒 | `number` | | 留空用 `plugin.collect.request-timeout-ms` |
| `retry` | 重试次数 | `number` | | 留空用 `plugin.collect.request-retry-times` |
| `retryInterval` | 重试间隔毫秒 | `number` | | |
| `proxy` | 代理 | `text` | | `127.0.0.1:7890` 或 `socks5://user:pwd@127.0.0.1:7890` |
| `userAgent` | User-Agent | `text` | | |
| `fetcher` | 抓取器 | `text` | | SPI 名称，默认 `http` |
| `outputVariable` | 出参变量名 | `select` | | options `["resp"]`，默认 `resp` |
| `saveDocument` | 保留文档对象 | `select` | | options `["1","0"]`，默认 `1` |
| `header:名称` | 请求头 | `pairs` | | 每行一条，存成参数 `header:名称` |
| `cookie:名称` | Cookie | `pairs` | | 每行一条，存成参数 `cookie:名称` |

受限请求头会被丢弃并告警：`connection`、`content-length`、`expect`、`host`、`upgrade`。

**`link`**
| name | label | type | 默认 |
|---|---|---|---|
| `source` | 页面来源 | `text` | `${respDoc}` |
| `selector` | 选择器 | `text` | `a[href]` |
| `attribute` | 取值的属性 | `text` | 留空按 `href`→`src`→`content`→`data-url` 取第一个非空 |
| `pattern` | 过滤正则 | `text` | 整串匹配 |
| `absolute` | 转绝对地址 | `select` `["1","0"]` | `1` |
| `limit` | 最多条数 | `number` | 留空/0 不限制 |
| `outputVariable` | 出参变量名 | `text` | `linkList` |

**`extract`**（`fieldEditor=true`）
| name | label | type | 默认 |
|---|---|---|---|
| `source` | 抽取来源 | `text` | `${respDoc}` |
| `mode` | 抽取模式 | `select` `["single","list"]` | `single` |
| `rowExpression` | 行选择器 | `text` | 列表模式用：HTML 填 CSS 选择器，JSON 填路径如 `data.items` |
| `outputVariable` | 出参变量名 | `text` | 列表模式 `rows`，单条模式 `row` |

**`sql`**
| name | label | type | required | 默认 |
|---|---|---|---|---|
| `statement` | SQL 语句 | `textarea` | ✔ | 整段支持 `${}`，一次一条语句 |
| `datasource` | 数据源 | `text` | | 留空/`app` 用主库，其余填 `collect_datasource` 的 ID 或名称 |
| `outputVariable` | 结果变量名 | `text` | | `rows` |

**`output`**
| name | label | type | required | 说明 |
|---|---|---|---|---|
| `each` | 逐条集合 | `text` | | 留空产出一条；填 `${rows}` 每项一条 |
| `cell:字段名` | 输出字段 | `pairs` | ✔ | 每行一条，存成参数 `cell:字段名` |
| `key` | 业务主键 | `text` | | 非空时同键覆盖写入 |
| `url` | 来源地址 | `text` | | 留空取 `${url}` |
| `targetTable` | 业务表 | `text` | | 留空只进采集结果表；填表名则同时写入，缺列自动补 |
| `datasource` | 业务表数据源 | `text` | | 留空/`app` 主库，其余填 ID 或名称 |

**`database`**（整批写库，不产采集记录）
| name | label | type | required | 说明 |
|---|---|---|---|---|
| `source` | 来源集合 | `textarea` | ✔ | 填集合表达式如 `${rows}`；也接受单条映射（按一行处理） |
| `datasource` | 目标数据源 | `datasource` | | 默认 `app`（应用主库） |
| `table` | 目标表 | `table` | | **允许填库里还不存在的表名**，首次运行时自动创建 |
| `mode` | 写入方式 | `select` `["append","replace"]` | | `append` 追加；`replace` 先清空目标表再写 |
| `column:目标列` | 字段映射 | `mapping` | ✔ | 每行一条，键为目标列名、值为来源字段或 `${}` 表达式 |
| `batchSize` | 每批行数 | `text` | | 默认 `500` |

表结构与字段归属一律委托数据源模块 `DatasourceSchemaService`（`TableDdlDTO` 策略 `APPEND`）：
表不存在则建（自带自增主键 id），已存在只补缺失列。**列类型按这一批实际取值推断**——
整数 `BIGINT`、小数 `DECIMAL`、布尔 `BOOLEAN`、字符串超过 200 字符按 `TEXT`、
集合/对象按 `JSON`，所以批量入库的列能正常排序与聚合，不是一律大文本。

> **来源集合为空时直接报错、且不动目标表**：覆盖写入拿空集合去清表，等于把
> 「今天没抓到数据」变成「把昨天唯一可信的那份一起删了」。此时任务失败、原表保留。
>
> **不提供 upsert**：跨方言的 `ON DUPLICATE KEY UPDATE` / `ON CONFLICT` 无法统一拼装，
> 要按业务主键去重请在流程里串一个 `dedup` 节点（`scope=global` 跨运行生效）。

**`variable`**
| name | label | type | options / 默认 |
|---|---|---|---|
| `set:变量名` | 赋值（变量名 = 表达式） | `pairs` | 存成参数 `set:变量名` |
| `scope` | 作用域 | `select` | `["branch","global"]`，默认 `branch` |

**`dedup`**
| name | label | type | required | options / 默认 |
|---|---|---|---|---|
| `key` | 主键表达式 | `text` | ✔ | 例 `${url}`、`${item.id}`，须与输出节点一致 |
| `scope` | 判重范围 | `select` | | `["flow","global"]`，默认 `flow` |

**`sleep`**：`millis`（等待毫秒, `number`）、`maxMillis`（随机上限, `number`）。
**`comment`**：`comment`（注释内容, `textarea`）。

---

## 1. CollectFlowController — 流程（9 个端点）

类级前缀：`@RequestMapping("/v2/collect/flow")`

| # | Method | Path | 摘要 | 返回 |
|---|---|---|---|---|
| 1 | GET | `/v2/collect/flow/page` | 流程分页列表 | **分页对象**（`ReturnPageResult<CollectFlowVO>`，双层包装，见 §0.5） |
| 2 | GET | `/v2/collect/flow/list` | 流程精简列表 | `List<CollectFlowVO>`（不含 `dsl`） |
| 3 | GET | `/v2/collect/flow/detail` | 流程详情 | `CollectFlowVO`（**唯一返回 `dsl` 的读接口**） |
| 4 | POST | `/v2/collect/flow/save` | 新增流程 | `CollectFlowVO`（等同 detail，含 `dsl`） |
| 5 | PUT | `/v2/collect/flow/update` | 修改流程 | `CollectFlowVO`（含 `dsl`） |
| 6 | PUT | `/v2/collect/flow/status` | 启停流程调度 | `CollectFlowVO`（含 `dsl`） |
| 7 | POST | `/v2/collect/flow/copy` | 复制流程 | `CollectFlowVO`（含 `dsl`） |
| 8 | DELETE | `/v2/collect/flow/delete` | 删除流程（逻辑删） | `Boolean` |
| 9 | POST | `/v2/collect/flow/validate` | 只校验流程图不落库 | `String`（成功时 `"流程图校验通过"`） |

### 参数

| 端点 | 参数 | 绑定 | 名称 | 类型 | 必填 |
|---|---|---|---|---|---|
| `/page` | 分页 | 对象（`@ParameterObject` + `RequestParamMapping`） | `page`\|`pageNo`\|`pageNumber`\|`pageNum`\|`current`；`pageSize`\|`size`\|`count` | Integer | 否（默认 1 / 10） |
| `/page` | 条件 | 对象（`@ModelAttribute` 语义） | 见下 | `CollectFlow` | 否 |
| `/list` | `keyword` | **无注解的简单类型**（按请求参数名绑定） | `keyword` | String | 否（缺失 → null，等价不过滤） |
| `/detail` | `flowId` | **无注解的简单类型** | `flowId` | Integer | 否（缺失 → Service 抛 `"流程ID不能为空"` → HTTP 400） |
| `/save` | `dto` | `@RequestBody` | — | `CollectFlowSaveDTO` | 是（body 缺失 → 400） |
| `/update` | `dto` | `@RequestBody` | — | `CollectFlowSaveDTO` | 是（`collectFlowId` 必须给） |
| `/status` | `flowId` | `@RequestParam` | `flowId` | Integer | **是** |
| `/status` | `status` | `@RequestParam` | `status` | Integer | **是**（只接受 `0`/`1`） |
| `/copy` | `flowId` | `@RequestParam`（POST 的 **query 参数**，不是 body） | `flowId` | Integer | **是** |
| `/delete` | `flowId` | `@RequestParam` | `flowId` | Integer | **是** |
| `/validate` | `dsl` | `@RequestBody` | — | `CollectDslDTO` | 是 |

`/page` 的查询条件（只有这 4 个字段真正进 WHERE）：

| 查询参数名 | 匹配方式 |
|---|---|
| `collectFlowName` | LIKE 模糊 |
| `collectFlowCode` | LIKE 模糊 |
| `collectFlowCategoryId` | 等值 |
| `collectFlowStatus` | 等值（`0`/`1`） |

固定 `WHERE collect_flow_is_deleted = 0`，`ORDER BY collect_flow_id DESC`；**SELECT 显式排除 `collect_flow_dsl` 列**，所以列表项的 `dsl` 恒为 `null`。

### DTO：`CollectFlowSaveDTO`（`dto/CollectFlowSaveDTO.java`）

| JSON 字段 | 类型 | 必填 | 说明（服务端实际校验） |
|---|---|---|---|
| `collectFlowId` | Integer | 新增留空，`update` 必填 | 主键 |
| `collectFlowName` | String | **是** | 非空、trim 后 ≤128，否则 400 |
| `collectFlowCategoryId` | Integer | 否 | 缺省 `0`（未分类） |
| `collectFlowCode` | String | 否 | 留空自动生成 `flow-<uuid8>`；≤64；未删除流程内唯一，重复报 `"流程编码已存在: x"` |
| `collectFlowCron` | String | 否 | 6 字段 Quartz 风格表达式；留空表示不定时 |
| `collectFlowConcurrency` | Integer | 否 | 缺省 `1`，上限 `32` |
| `collectFlowThreadCount` | Integer | 否 | 缺省 `plugin.collect.default-thread-count`(4)，上限 `64` |
| `collectFlowRemark` | String | 否 | |
| `dsl` | `CollectDslDTO` | **是** | 保存前会 `normalizeDsl` 校验（见 §11），校验失败 400 |

> DTO 上**没有任何** `@NotNull/@NotBlank` 注解（全模块 DTO/entity/vo 均无 Bean Validation 注解），必填语义全部在 Service 里用 `IllegalArgumentException` 实现。

### VO：`CollectFlowVO`（`vo/CollectFlowVO.java`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `collectFlowId` | Integer | |
| `collectFlowName` | String | |
| `collectFlowCategoryId` | Integer | |
| `collectFlowCategoryName` | String | 未分类时为 `"未分类"` |
| `collectFlowCode` | String | |
| `collectFlowCron` | String | |
| `collectFlowStatus` | Integer | 0 停用 1 启用 |
| `collectFlowConcurrency` | Integer | |
| `collectFlowThreadCount` | Integer | 空时回填平台默认值（不会是 null） |
| `collectFlowRemark` | String | |
| `collectFlowLastExecTime` | LocalDateTime | `yyyy-MM-dd HH:mm:ss`（GMT+8） |
| `collectFlowNextExecTime` | LocalDateTime | 取自 job-starter `sys_job` 台账的下次触发时刻；流程未登记或已停用时为 `null` |
| `collectFlowExecCount` | Integer | |
| `nodeCount` | Integer | **仅 detail/save/update/copy 返回**；page/list 为 null |
| `edgeCount` | Integer | 同上 |
| `running` | Boolean | 本机是否有该流程运行中任务 |
| `scheduled` | Boolean | 调度台账里已登记该流程且任务处理器可用 |
| `dsl` | `CollectDslDTO` | **仅 detail / 写操作返回**，page/list 恒为 null |
| `createName` | String | |
| `createTime` | LocalDateTime | `yyyy-MM-dd HH:mm:ss` |
| `updateTime` | LocalDateTime | `yyyy-MM-dd HH:mm:ss` |

### 分页 JSON 示例：`GET /collect/api/v2/collect/flow/page?pageNo=1&size=20&collectFlowStatus=1`

```json
{
  "code": "00000", "msg": "操作成功", "ok": true, "success": true,
  "data": {
    "code": 0, "msg": "success",
    "data": {
      "pageNo": 1, "pageSize": 20, "totalPages": 1, "total": 1,
      "data": [
        {
          "collectFlowId": 1, "collectFlowName": "示例流程", "collectFlowCategoryId": 0,
          "collectFlowCategoryName": "未分类", "collectFlowCode": "flow-1a2b3c4d",
          "collectFlowCron": "0 0/10 * * * ?", "collectFlowStatus": 1,
          "collectFlowConcurrency": 1, "collectFlowThreadCount": 4, "collectFlowRemark": null,
          "collectFlowLastExecTime": "2026-09-29 10:00:00", "collectFlowNextExecTime": "2026-09-29 10:10:00",
          "collectFlowExecCount": 3, "nodeCount": null, "edgeCount": null, "running": false, "scheduled": true,
          "dsl": null, "createName": "admin",
          "createTime": "2026-09-29 09:00:00", "updateTime": "2026-09-29 10:00:00"
        }
      ]
    }
  }
}
```

---

## 2. CollectTaskController — 任务（6 个端点）

前缀 `/v2/collect/task`

| # | Method | Path | 摘要 | 参数 | 返回 |
|---|---|---|---|---|---|
| 10 | GET | `/v2/collect/task/page` | 任务分页 | 分页对象 + `CollectTask` 条件对象 | **分页**`CollectTaskVO` |
| 11 | GET | `/v2/collect/task/detail` | 任务详情 | `taskId` `@RequestParam` Long **必填** | `CollectTaskVO` 单个 |
| 12 | GET | `/v2/collect/task/logs` | 节点执行日志（按执行顺序） | `taskId` `@RequestParam` Long **必填** | `List<CollectTaskLogVO>` |
| 13 | GET | `/v2/collect/task/node-states` | 每节点最近一次状态（节点着色） | `taskId` `@RequestParam` Long **必填** | `List<CollectTaskLogVO>` |
| 14 | DELETE | `/v2/collect/task/delete` | 删除任务及日志 | `taskId` `@RequestParam` Long **必填** | `Boolean` |
| 15 | POST | `/v2/collect/task/clean` | 清理历史任务 | `flowId` `@RequestParam`(required=false) Integer；`days` `@RequestParam`(defaultValue="7") int | `Integer`（删除条数） |

`/page` 真正生效的条件字段：`collectTaskFlowId`(eq)、`collectTaskStatus`(eq)、`collectTaskTriggerType`(eq)、`collectTaskNo`(like)、`collectTaskFlowName`(like)；固定 `ORDER BY collect_task_id DESC`。
`/logs` 与 `/node-states` 单次上限 **1000 条**（取最近 1000 条；`logs` 会反转成执行顺序，`node-states` 按 `collectTaskLogNodeId` 去重保留最新）。

### VO：`CollectTaskVO`

`collectTaskId` Long、`collectTaskNo` String、`collectTaskFlowId` Integer、`collectTaskFlowName` String、
`collectTaskStatus` String(RUNNING/SUCCESS/FAILURE/STOPPED)、`collectTaskTriggerType` String(CRON/MANUAL/TEST)、
`collectTaskParams` String(入参覆盖 JSON 原文，需前端自行 `JSON.parse`)、
`collectTaskBeginTime`/`collectTaskEndTime` LocalDateTime(`yyyy-MM-dd HH:mm:ss`)、`collectTaskCost` Long、
`collectTaskNodeCount` Integer、`collectTaskRequestCount` Integer、`collectTaskRecordCount` Integer、
`collectTaskError` String、`collectTaskExecutorId` String、`running` Boolean、`createName` String。

> 注意：`/execution/run` 与 `/execution/dry-run` 返回的也是 `CollectTaskVO`，但异步提交时只有部分字段被填充
> （`collectTaskId`/`collectTaskNo`/`collectTaskFlowId`/`collectTaskFlowName`/`collectTaskStatus`/`collectTaskTriggerType`/
> `collectTaskParams`/`collectTaskBeginTime`/`collectTaskExecutorId`/`running=true`），
> 结束时间与统计要轮询 `/task/detail` 拿。

### VO：`CollectTaskLogVO`

`collectTaskLogId` Long、`collectTaskLogTaskId` Long、`collectTaskLogNodeId` String、`collectTaskLogNodeName` String、
`collectTaskLogNodeShape` String、`collectTaskLogIteration` Integer、`collectTaskLogStatus` String(SUCCESS/FAILURE/SKIPPED)、
`collectTaskLogCost` Long、`collectTaskLogMessage` String、`collectTaskLogOutput` String（出参预览，长度受 `plugin.collect.preview-length` 默认 500 截断）、
`createTime` LocalDateTime。
（实体的 `collectTaskLogFlowId` **不在** VO 中。）

---

## 3. CollectExecutionController — 执行（5 个端点）

前缀 `/v2/collect/execution`

| # | Method | Path | 摘要 | 参数 | 返回 |
|---|---|---|---|---|---|
| 16 | POST | `/v2/collect/execution/run` | 运行已保存流程（异步，立即返回任务号） | `@RequestBody CollectRunDTO` | `CollectTaskVO` 单个 |
| 17 | POST | `/v2/collect/execution/dry-run` | 试运行未保存的流程图，结果不落库 | `@RequestBody CollectDslDTO` | `CollectTaskVO` 单个 |
| 18 | POST | `/v2/collect/execution/stop` | 停止任务 | `taskId` `@RequestParam` Long **必填**（**query 参数，无 body**） | `Boolean`（是否命中本机运行中任务） |
| 19 | GET | `/v2/collect/execution/running` | 本机执行中任务数 | 无 | `Integer` |
| 20 | GET | `/v2/collect/execution/inflight` | 某流程排队+执行中任务数 | `flowId` `@RequestParam` Integer **必填** | `Integer` |

### DTO：`CollectRunDTO`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `collectFlowId` | Integer | **是**（null → 400 `"必须指定要运行的流程"`） | |
| `variables` | `CollectParamDTO[]` | 否 | 本次执行的变量覆盖，仅覆盖本次 |
| `persist` | Boolean | 否 | 默认 `true`。`true`→触发方式 `MANUAL` 且结果落库；`false`→`TEST` 且结果不落库 |

并发保护：同一流程在途任务数超过 `collectFlowConcurrency` 时抛 `IllegalStateException` → **HTTP 409**，
`msg` 形如 `"流程 12 并发任务数已达上限 1，本次触发跳过"`。

---

## 4. CollectRecordController — 采集结果（6 个端点）

前缀 `/v2/collect/record`

| # | Method | Path | 摘要 | 参数 | 返回 |
|---|---|---|---|---|---|
| 21 | GET | `/v2/collect/record/page` | 结果分页 | 分页对象 + `CollectRecord` 条件对象 | **分页**`CollectRecordVO` |
| 22 | GET | `/v2/collect/record/detail` | 结果详情 | `recordId` `@RequestParam` Long **必填** | `CollectRecordVO` 单个 |
| 23 | DELETE | `/v2/collect/record/delete` | 删除单条 | `recordId` `@RequestParam` Long **必填** | `Boolean` |
| 24 | POST | `/v2/collect/record/deletes` | 批量删除 | `@RequestBody List<Long>`（**body 是裸 JSON 数组**，如 `[1,2,3]`） | `Integer`（删除条数） |
| 25 | DELETE | `/v2/collect/record/clear` | 清空结果 | `flowId` `@RequestParam`(required=false) Integer；`taskId` `@RequestParam`(required=false) Long；**两者至少给一个**，否则 400 `"必须指定流程或任务"` | `Integer` |
| 26 | GET | `/v2/collect/record/count` | 结果总数 | `flowId` `@RequestParam`(required=false) Integer（空=全库） | `Long` |

`/page` 生效的条件字段：`collectRecordFlowId`(eq)、`collectRecordTaskId`(eq)、`collectRecordNodeId`(eq)、
`collectRecordKey`(like)、`collectRecordUrl`(like)；固定 `ORDER BY collect_record_id DESC`。

### VO：`CollectRecordVO`

`collectRecordId` Long、`collectRecordFlowId` Integer、`collectRecordTaskId` Long、`collectRecordNodeId` String、
`collectRecordKey` String、`collectRecordUrl` String、
`cells` `CollectParamDTO[]`（**有序**字段值，`{name,value,description}`；由 payload JSON 展开，`description` 为 null）、
`createTime`/`updateTime` LocalDateTime。

> 实体的 `collectRecordPayload`（原始 JSON 串）**不下发**，前端用 `cells` 渲染表格；列顺序/标题由 `/v2/collect/field/list` 决定。
> 单元格值统一是 String（复杂结构由服务端序列化成 JSON 文本）。

---

## 5. CollectFieldController — 结果字段定义（2 个端点）

前缀 `/v2/collect/field`

| # | Method | Path | 摘要 | 参数 | 返回 |
|---|---|---|---|---|---|
| 27 | GET | `/v2/collect/field/list` | 某流程/节点的字段定义 | `flowId` `@RequestParam` Integer **必填**；`nodeId` `@RequestParam`(required=false) String（空=流程级字段） | `List<CollectField>`（**实体直出**） |
| 28 | POST | `/v2/collect/field/replace` | 整体替换字段定义（先删后插） | `@RequestBody CollectFieldSaveDTO` | `Boolean` |

### DTO：`CollectFieldSaveDTO`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `collectFlowId` | Integer | **是**（null → 400 `"流程ID不能为空"`） | 注意字段名带 `collect` 前缀 |
| `collectNodeId` | String | 否 | 空/null → 存成空串（流程级） |
| `fields` | `CollectField[]` | 否 | 空数组＝清空该节点全部字段 |

### 实体：`CollectField`（`/field/list` 的元素、`fields` 的元素）

| 字段 | 类型 | 说明 |
|---|---|---|
| `collectFieldId` | Integer | 提交时服务端强制置 null（重新插入） |
| `collectFieldFlowId` | Integer | 服务端用入参的 `collectFlowId` 覆盖 |
| `collectFieldNodeId` | String | 服务端用 `collectNodeId` 覆盖 |
| `collectFieldName` | String | **必填**，同批次内不可重复 |
| `collectFieldLabel` | String | 留空时＝`collectFieldName` |
| `collectFieldType` | String | 留空时 `STRING`；可选 `STRING`/`INT`/`LONG`/`DECIMAL`/`DATE`/`TEXT` |
| `collectFieldPrimary` | Integer | 留空时 `0`；`1`＝业务主键（同键覆盖） |
| `collectFieldLength` | Integer | 导出建表字符串长度，0/留空取默认 |
| `collectFieldSort` | Integer | 留空时按数组下标自动编号 |
| `collectFieldEnabled` | Integer | 留空时 `1`；`0` 隐藏列 |
| `createName` / `createBy` / `createTime` / `updateName` / `updateBy` | String/Integer/LocalDateTime | 继承 `SysBase`（`createTime`、`updateTime` 为 `yyyy-MM-dd HH:mm:ss`） |

---

## 6. CollectVariableController — 全局变量（4 个端点）

前缀 `/v2/collect/variable`

| # | Method | Path | 摘要 | 参数 | 返回 |
|---|---|---|---|---|---|
| 29 | GET | `/v2/collect/variable/list` | 变量列表（密文值脱敏） | 无 | `List<CollectVariable>`（按名称升序） |
| 30 | POST | `/v2/collect/variable/save` | 新增变量 | `@RequestBody CollectVariable` | `CollectVariable` 单个（密文值回掩码） |
| 31 | PUT | `/v2/collect/variable/update` | 修改变量 | `@RequestBody CollectVariable` | `Boolean` |
| 32 | DELETE | `/v2/collect/variable/delete` | 删除变量 | `variableId` `@RequestParam` Integer **必填** | `Boolean` |

### 实体：`CollectVariable`（请求体与响应体同一个类）

| 字段 | 类型 | 说明 |
|---|---|---|
| `collectVariableId` | Integer | 新增时必须留空（服务端强制置 null） |
| `collectVariableName` | String | **必填**，`[A-Za-z_][A-Za-z0-9_]{0,63}`，全局唯一 |
| `collectVariableValue` | String | 密文变量读回为 `"******"`；`update` 时提交掩码/`null` 表示不改值 |
| `collectVariableEncrypted` | Integer | `0` 明文 / `1` 密文（密文以 `b64:` 前缀存储） |
| `collectVariableDescription` | String | |
| `createName`/`createBy`/`createTime`/`updateName`/`updateBy` | — | `SysBase` |

---

## 7. CollectDatasourceController — 数据源（8 个端点）

前缀 `/v2/collect/datasource`

| # | Method | Path | 摘要 | 参数 | 返回 |
|---|---|---|---|---|---|
| 33 | GET | `/v2/collect/datasource/list` | 数据源列表 | 无 | `List<CollectDatasourceVO>`（按名称升序） |
| 34 | GET | `/v2/collect/datasource/options` | 下拉选项 | 无 | `List<CollectOptionVO>`（**首项固定为 `app`＝应用主库（默认）**） |
| 35 | POST | `/v2/collect/datasource/save` | 新增 | `@RequestBody CollectDatasource` | `CollectDatasourceVO` 单个 |
| 36 | PUT | `/v2/collect/datasource/update` | 修改（密码留掩码表示不改） | `@RequestBody CollectDatasource` | `Boolean` |
| 37 | DELETE | `/v2/collect/datasource/delete` | 删除（被流程引用时拒绝） | `datasourceId` `@RequestParam` Integer **必填** | `Boolean` |
| 38 | POST | `/v2/collect/datasource/test` | 测试连接（可用未保存配置） | `@RequestBody CollectDatasource` | `String`（数据库产品信息） |
| 39 | GET | `/v2/collect/datasource/tables` | 表清单（`database` 节点目标表下拉） | `datasource` 可选 | `List<String>` |
| 40 | GET | `/v2/collect/datasource/columns` | 列元数据（字段映射左列候选） | `datasource` 可选、`table` **必填** | `List<CollectColumnVO>` |

> **`/tables` 与 `/columns` 的调用约定**（`FlowDesigner.vue` 依赖）：
> 目标表**允许在库里还不存在**。前端不靠「读列报错」判断表不存在——读列失败还有数据源不可达、
> 权限不足、连接超时等多种原因，一律当成"表不存在"会把真故障静默掉；
> 而是用 `/tables` 的返回清单做集合判定：清单里没有就是新表，**前端据此跳过 `/columns`**。
> 直接去读一张不存在的表必然失败，得到的只是一条红字报错，把"运行时会自动建表"这件
> 正常事显示成故障。字段映射控件随之退化成自由输入列名（新建表模式），并停用
> 「N 个字段在当前表里不存在」的告警——新列本来就不在库里。

### 请求体实体：`CollectDatasource`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `collectDatasourceId` | Integer | `update` 必填 | 新增留空；`test` 时给了 ID 会用已存配置补齐缺省字段 |
| `collectDatasourceName` | String | **是** | 唯一；不可等于 `app`（保留字） |
| `collectDatasourceType` | String | 否 | `MYSQL`/`H2`/`POSTGRESQL` |
| `collectDatasourceDriverClassName` | String | 否 | |
| `collectDatasourceJdbcUrl` | String | **是** | 必须以 `jdbc:` 开头，≤500 |
| `collectDatasourceUsername` | String | 否 | |
| `collectDatasourcePassword` | String | 否 | 提交明文，服务端以 `b64:` 前缀存储；**读接口不返回**；`update` 时留空/掩码＝不改 |
| `collectDatasourceStatus` | Integer | 否 | 新增留空默认 `1` |
| `collectDatasourceRemark` | String | 否 | |
| `createName`/`createBy`/`createTime`/`updateName`/`updateBy` | — | | `SysBase` |

### VO：`CollectDatasourceVO`

`collectDatasourceId` Integer、`collectDatasourceName` String、`collectDatasourceType` String、
`collectDatasourceDriverClassName` String、`collectDatasourceJdbcUrl` String、`collectDatasourceUsername` String、
`passwordSet` **Boolean**（替代密码字段，VO 里**没有** `collectDatasourcePassword`）、
`collectDatasourceStatus` Integer、`collectDatasourceRemark` String、`createTime`/`updateTime` LocalDateTime。

---

## 8. CollectCategoryController — 分类（5 个端点）

前缀 `/v2/collect/category`

| # | Method | Path | 摘要 | 参数 | 返回 |
|---|---|---|---|---|---|
| 39 | GET | `/v2/collect/category/tree` | 分类树 | 无 | `List<CollectCategoryVO>`（根节点数组，子级在 `children`） |
| 40 | GET | `/v2/collect/category/options` | 下拉选项 | 无 | `List<CollectOptionVO>`（**首项固定 `value:"0"` label:`"未分类"`**） |
| 41 | POST | `/v2/collect/category/save` | 新增分类 | `@RequestBody CollectCategory` | `CollectCategory` 单个（回填 `collectCategoryId`、`collectCategoryTreeId`） |
| 42 | PUT | `/v2/collect/category/update` | 修改分类 | `@RequestBody CollectCategory` | `Boolean` |
| 43 | DELETE | `/v2/collect/category/delete` | 删除（有子分类或流程时拒绝） | `categoryId` `@RequestParam` Integer **必填** | `Boolean` |

### 实体：`CollectCategory`（请求体/响应体）

| 字段 | 类型 | 说明 |
|---|---|---|
| `collectCategoryId` | Integer | 新增留空 |
| `collectCategoryName` | String | **必填**（trim 后非空） |
| `collectCategoryPid` | Integer | 顶级为 `0`（或 null）；不可为自己、不可在自己子树内 |
| `collectCategoryTreeId` | String | 路径链 `/0/1/3/`，服务端计算，前端只读 |
| `collectCategorySort` | Integer | 留空时 `0`，越小越靠前 |
| `collectCategoryRemark` | String | |
| `createName`/`createBy`/`createTime`/`updateName`/`updateBy` | — | `SysBase` |

### VO：`CollectCategoryVO`

`collectCategoryId` Integer、`collectCategoryName` String、`collectCategoryPid` Integer（null→`0`）、
`collectCategorySort` Integer、`collectCategoryRemark` String、`flowCount` Integer（**该分类直属**流程数，不含子分类）、
`children` `CollectCategoryVO[]`（默认 `[]`，非 null）。
（树里**没有** `collectCategoryTreeId`。）

### VO：`CollectOptionVO`（分类/数据源下拉通用）

`value` String（注意：**始终是字符串**，ID 也被转成 String）、`label` String。

---

## 9. CollectDesignerController — 设计器元数据（3 个端点）

前缀 `/v2/collect/designer`

| # | Method | Path | 摘要 | 参数 | 返回 |
|---|---|---|---|---|---|
| 44 | GET | `/v2/collect/designer/metas` | 全部节点类型元数据 | 无 | `List<CollectShapeMeta>` |
| 45 | GET | `/v2/collect/designer/runtime` | 运行时概览 | 无 | `CollectRuntimeVO` 单个（内含 `shapes: CollectShapeMeta[]`，与 metas 同源） |
| 46 | GET | `/v2/collect/designer/cron/next` | 预览定时触发时间 | `cron` `@RequestParam` String **必填**；`times` `@RequestParam`(defaultValue="5") int | `List<LocalDateTime>` |

`/cron/next`：`times` 会被夹到 `[1, 10]`；表达式非法直接 400（中文提示）；返回的 `LocalDateTime` **没有** `@JsonFormat`，
序列化为 ISO 数组形式还是字符串取决于全局 Jackson 配置，前端建议按字符串解析。

### VO：`CollectRuntimeVO`

`enable` Boolean、`executorId` String（`主机名:pid`）、`runningTasks` Integer、`flowCount` Long、`enabledFlowCount` Long、
`taskCount` Long、`recordCount` Long、`schedulerGroup` String（`collect-flow`，即 job-starter `sys_job` 的分组）、
`scheduledJobCount` Long（该分组下已登记的流程数，正常应等于 `flowCount`）、
`taskTimeoutMs` Long（`plugin.collect.task-timeout-ms`，默认 1800000，即 30 分钟；
分片型采集常在 10 分钟以上，旧默认 600000 会把正常任务判成超时失败）、`maxRecordPerTask` Integer（默认 100000）、
`defaultThreadCount` Integer（默认 4）、`requestTimeoutMs` Integer（默认 30000）、`shapes` `CollectShapeMeta[]`。

### VO：`CollectShapeMeta`（`engine/spi/CollectShapeMeta.java`）— `/metas` 的元素

| 字段 | 类型 | 语义 |
|---|---|---|
| `shape` | String | 节点类型标识，与 `nodes[].shape` 一致（取值见 §0.9） |
| `label` | String | 工具箱显示名 |
| `group` | String | 分组，仅 4 个值：`来源`/`解析`/`控制`/`输出`（前端按它分组/着色） |
| `icon` | String | 图标标识：`play`/`globe`/`link`/`filter`/`database`/`sigma`/`shield`/`clock`/`note` |
| `description` | String | 功能说明 |
| `outputVariable` | String | 出参变量说明文案；**不是所有节点都有**（`comment`、`variable`、`sleep`、`dedup` 及所有走 `CollectShapeMetas.of` 未额外赋值的节点为 `null`） |
| `fieldEditor` | boolean | **原始 boolean，恒序列化**；只有 `extract` 为 `true` → 前端仅对该 shape 渲染抽取字段表格 |
| `params` | `CollectParamMeta[]` | 属性面板字段清单，顺序即渲染顺序；默认 `[]` |

### VO：`CollectParamMeta`（`engine/spi/CollectParamMeta.java`）

| 字段 | 类型 | 语义 / 取值 |
|---|---|---|
| `name` | String | 参数名，写进 `nodes[].params[].name`。可重复参数组是**带占位的字面量**：`header:名称`、`cookie:名称`、`set:变量名`、`cell:字段名` —— 前端要把 `冒号` 前缀当组名、后缀当"键名"输入框，实际提交的 name 是 `header:User-Agent` 这种拼好的串 |
| `label` | String | 显示标题 |
| `type` | String | 控件类型，声明支持 `text`/`textarea`/`number`/`switch`/`select`/`pairs`/`fields`；**默认值 `"text"`**。当前 10 个 shape 实际只出现 `text`、`textarea`、`number`、`select`、`pairs`（`switch`、`fields` 是预留，内置 designer.js 把 `select` 和 `switch` 走同一分支） |
| `options` | String[] | 仅 `type=select` 有效；非 select 时为 `[]`（**不会是 null**）。取值集合见 §0.9 各表 |
| `defaultValue` | String | 默认值，**始终字符串**（如 `"0"`、`"${respDoc}"`、`"a[href]"`）；新建节点时前端应以此初始化 `params[].value`；未设置时为 `null` |
| `required` | boolean | **原始 boolean，恒序列化**（默认 false） |
| `tip` | String | 填写提示（表达式写法说明），可能为 `null` |

> `metas` 的数组顺序 = 执行器注册顺序（Spring `orderedStream()` + SPI），**不保证稳定**，前端要按 `shape` 建索引、按 `group` 自行分组，别依赖返回顺序。

---

## 10. 已删除的控制台登录控制器

> **2026-09-26 校订**：本节原文描述的 `CollectConsoleAuthController`
> （前缀 `/collect-console/auth`，3 个端点 `status` / `login` / `logout`）及其
> DTO `CollectLoginDTO`、VO `CollectConsoleStatusVO` **在当前代码中不存在**。
> 登录改由框架 oauth `static` 协议承担，接口是 `POST /v2/user/login`（见 §0.6），不归本模块所有。
> 访问 `/collect-console/auth/status` 会得到 **403**（平台层无此 handler），这是预期行为，不是缺陷。

原文的「已删除：内置控制台页面与页面跳转控制器」一段仍然成立，保留备查：
`CollectConsoleController`（`/collect-console`、`/collect-console/login` 两个 302 入口）与
`resources/static/collect-console/**` 已删除，现在访问这些地址：静态资源 **404**，
`/collect-console` 根路径 **403**，不再跳登录页。

---

## 10bis. CollectFunctionController — 自定义函数（5 个端点）

> **2026-09-26 新增**：初版文档遗漏了整个控制器。

前缀 `/v2/collect/function`。函数保存前先编译，编译不过直接拒绝；登记后
流程参数表达式 `${name(...)}` 与脚本节点里的 `fn.name(...)` 都能调。
函数名不能与内置函数同名（内置函数表实测 **58** 个，见 `CollectRuntimeVO.builtinFunctions`）。

| # | Method | Path | 摘要 | 参数 | 返回 |
|---|---|---|---|---|---|
| 1 | GET | `/v2/collect/function/list` | 函数列表（`keyword` 模糊匹配函数名与说明） | `keyword` | `List<CollectFunction>` |
| 2 | POST | `/v2/collect/function/save` | 新增（编译不通过则拒绝） | `@RequestBody CollectFunction` | `CollectFunction` |
| 3 | PUT | `/v2/collect/function/update` | 修改 | `@RequestBody CollectFunction` | `Boolean` |
| 4 | DELETE | `/v2/collect/function/delete` | 删除 | `functionId` `@RequestParam` | `Boolean` |
| 5 | POST | `/v2/collect/function/test` | 试跑（实参按位置传） | `@RequestBody CollectFunctionTestDTO` | `CollectFunctionTestVO` |

模块开关 `plugin.collect.function.enable`（默认 `true`）；`false` 时自定义函数不参与解析，只剩内置函数。

---

## 10ter. CollectNoticeController — 执行通知（6 个端点）

> **2026-09-26 新增**：初版文档遗漏了整个控制器。

前缀 `/v2/collect/notice`。任务进入终态后按命中时机**异步**投递，投递失败只记在该配置的
「最近结果」里，**不改任务终态**。可用渠道来自后端注册表（Spring Bean + SPI），前端不写死。

> **通知已彻底全局化**：库里没有 `collect_notice_flow_id`，`/list` 也不再接 `flowId`，
> 一条配置对每次执行都参与命中，只靠「时机」筛。

| # | Method | Path | 摘要 | 参数 | 返回 |
|---|---|---|---|---|---|
| 1 | GET | `/v2/collect/notice/list` | 通知配置列表（全平台一份） | 无 | `List<CollectNotice>` |
| 2 | GET | `/v2/collect/notice/channels` | 可用渠道注册表 | 无 | `List<CollectNotifyChannelVO>` |
| 3 | POST | `/v2/collect/notice/save` | 新增 | `@RequestBody CollectNotice` | `CollectNotice` |
| 4 | PUT | `/v2/collect/notice/update` | 修改 | `@RequestBody CollectNotice` | `Boolean` |
| 5 | DELETE | `/v2/collect/notice/delete` | 删除 | `noticeId` `@RequestParam` | `Boolean` |
| 6 | POST | `/v2/collect/notice/test` | 试发一条（结果写进「最近结果」） | `noticeId` `@RequestParam` | `CollectNotifyResult` |

实测 `/channels` 返回 **2 个**渠道。模块开关 `plugin.collect.notify.enable`（默认 `true`）。
时机判定见 `support/CollectNotifyTiming`：命中 `FAILURE` 才发的配置在任务非失败终态时不投递；
手动停止与跳过的任务只有「每次执行后」会发。

---

## 10quater. CollectSchedulerConsoleController — 调度控制台出口（12 个端点）

> **2026-09-26 新增**：初版文档遗漏了整个控制器。
> 这是「调度模块用既有 job-starter」这条要求的落点：**不重写治理逻辑，只复用**。

前缀 `/v2/scheduler`，与 job-starter 自带的 `/v2/job/**` 共用同一个
`com.chua.starter.job.support.manage.JobGovernanceService`，两组地址都受统一认证过滤器保护。
挂在 `/v2/scheduler` 而不是并入 `/v2/collect/**`，是因为调度与采集是两个平级系统，
只共用 `sys_job` 台账。**本类不带 `@Permission("admin")`**，业务归属由各页面自己把关。

| # | Method | Path | 摘要 | 返回 |
|---|---|---|---|---|
| 1 | GET | `/v2/scheduler/summary` | 工作台概览（角色、选主态、任务与日志计数） | `JobSummaryVO` |
| 2 | GET | `/v2/scheduler/page` | 任务运行态分页 | **分页**`JobRuntimeVO` |
| 3 | GET | `/v2/scheduler/detail/{jobKey}` | 按任务键取运行态 | `JobRuntimeVO` |
| 4 | POST | `/v2/scheduler/save` | 登记或更新任务（调度配置与启停一次写入） | `Boolean` |
| 5 | PUT | `/v2/scheduler/schedule` | 只改调度配置并立即生效 | `Boolean` |
| 6 | PUT | `/v2/scheduler/status` | 启停自动调度 | `Boolean` |
| 7 | POST | `/v2/scheduler/trigger` | 立即派发一次（停用任务会被拒绝） | `Boolean` |
| 8 | DELETE | `/v2/scheduler/{jobKey}` | 删除任务（先停调度再删台账行） | `Boolean` |
| 9 | GET | `/v2/scheduler/executors` | 本节点已注册执行器（`@Job` 扫描 + 编程式注入） | `List<JobExecutorVO>` |
| 10 | GET | `/v2/scheduler/nodes` | 在线执行器节点清单 | `List<JobNodeVO>` |
| 11 | GET | `/v2/scheduler/log/page` | 执行日志分页（按触发时刻倒序） | **分页**`JobLogVO` |
| 12 | POST | `/v2/scheduler/log/cleanup` | 清理执行日志（`retentionDays=0` 清空全部） | `Integer` |

整组受 `plugin.job.manage.enable` 控制（`application.yml` 里为 `true`；置 `false` 则整个控制器不装配）。

**采集流程如何进台账**：`CollectFlowJobHandler` 把启用中的流程登记成 `sys_job` 行，
分组固定为 `collect-flow`，任务键形如 `collect-flow-<流程ID>`。实测一条 cron 为 `0 0/30 * * * ?`
的流程在 `/v2/scheduler/page` 里显示为分组 `collect-flow`、下次触发 `2026-09-26 11:00:00`。
`CollectFlowVO.collectFlowNextExecTime` 就取自这里；流程未登记或已停用时为 `null`。

> `sys_job` 的种子 SQL（`V1.0__initdata_all_job.sql`）是**空占位**（"本模块暂无种子数据"）。
> 台账里的行全部由流程登记或外部调用 `save` 产生。

---

## 11. DSL 结构（设计器保存/试运行的请求体）

### `CollectDslDTO`（`dto/CollectDslDTO.java`）

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `name` | String | null | 流程名称（detail 读取时若空会回填流程名） |
| `threadCount` | Integer | null | 并行线程数；**服务端按流程/平台配置覆盖，前端传了也不算** |
| `headers` | `CollectParamDTO[]` | `[]` | 全局请求头 |
| `cookies` | `CollectParamDTO[]` | `[]` | 全局 Cookie |
| `variables` | `CollectParamDTO[]` | `[]` | 流程级变量（detail 返回时= 全局变量 + 流程变量的合并结果） |
| `nodes` | `CollectNodeDTO[]` | `[]` | **不能为空**，否则 400 `"流程图没有任何节点"` |
| `edges` | `CollectEdgeDTO[]` | `[]` | |

### `CollectNodeDTO`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | String | **必填**、图内唯一（重复报 `"节点 ID 重复: x"`） |
| `shape` | String | **必填**，且必须是 §0.9 中已注册的值（否则 `"节点 x 的类型未注册: y"`） |
| `name` | String | 显示名，留空时服务端回退为 `shape-id` |
| `x` | Integer | 画布横坐标 |
| `y` | Integer | 画布纵坐标 |
| `params` | `CollectParamDTO[]` | **数组，不是 map**（顺序与重复项都保留），见下 |
| `fields` | `CollectExtractFieldDTO[]` | 抽取字段声明（`extract` 节点用） |

`params` 是 **`[{name,value,description}]` 数组**，不是 map。
可重复参数靠 `name` 的 `组名:键名` 前缀表达，例如：

```json
"params": [
  { "name": "loopCount", "value": "", "description": "循环集合/次数" },
  { "name": "loopVariableName", "value": "index", "description": "下标变量名" },
  { "name": "loopItemVariableName", "value": "item", "description": "元素变量名" },
  { "name": "loopParallel", "value": "0", "description": "迭代并行" },
  { "name": "url", "value": "https://example.com/list?p=${index}", "description": "URL" },
  { "name": "method", "value": "GET", "description": "请求方法" },
  { "name": "header:User-Agent", "value": "curl/8", "description": "请求头" },
  { "name": "cookie:SID", "value": "abc", "description": "Cookie" }
]
```

取值语义（`engine/model/CollectNode`）：`param(name)` 取该 name 的**首个非空值并 trim**；同 name 多条会全部忽略后续条目 —— 普通参数**不要**重复写同名。
`pairsOf("header")` 会挑出所有 `header:` 前缀项并剥前缀，按声明顺序返回，**重复键保留**。
`intParam`/`longParam` 解析失败静默用默认值；`boolParam` 认 `"1"` 与 `"true"`（大小写不敏感）。

### `CollectParamDTO`（也用于 `headers`/`cookies`/`variables`/`RecordVO.cells`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | String | 参数名/头名/变量名/字段名 |
| `value` | String | 值（**始终字符串**） |
| `description` | String | 说明；内置控制台用它存 meta 的 label |

### `CollectExtractFieldDTO`（`nodes[].fields[]`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | String | 字段名；空/纯空白会被跳过 |
| `parserType` | String | `CSS`(默认)/`REGEX`/`JSON`/`TEXT`/`HTML` |
| `expression` | String | CSS 选择器 / 正则 / JSON 点号路径（支持 `$.a.b[0]` 与 `a.b`） |
| `attribute` | String | 仅 CSS：`text`(默认)/`attr`/`html`/`outer` |
| `attrName` | String | `attribute=attr` 时的属性名 |
| `group` | Integer | 正则捕获组序号，默认按 `1` |
| `defaultValue` | String | 抽为空时的默认值（**会按表达式求值**，不是字面量） |

### `CollectEdgeDTO`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | String | 连线 ID |
| `from` | String | 源节点 ID（必须存在，否则 `"连线 x 的端点不存在"`） |
| `to` | String | 目标节点 ID；**不允许自连** |
| `condition` | String | 流转条件表达式，留空＝总放行 |
| `exceptionFlow` | String | `"0"` 总是 / `"1"` 仅异常 / `"2"` 仅正常；**空串/null 等同 `"0"`**。只有 `"1"` 会把节点失败算作「已被处理」，详见 §0.8 |
| `transmitVariable` | Boolean | 是否传递上游变量；**null 视为 `true`** |

### 校验规则（`normalizeDsl` / `CollectGraph.build`，`/flow/save|update`、`/flow/validate`、`/execution/dry-run` 都会跑）

1. `nodes` 非空；每个节点 `id`、`shape` 非空、`shape` 已注册、`id` 不重复。
2. 每条边两端点必须存在、不得自连。
3. **不允许环路**，违反报 `"流程存在环路: x，循环请使用节点的循环次数参数表达"`（循环靠 `loopCount`）。
4. 必须有入口：优先 `shape=="start"` 的节点，没有则取入度为 0 的节点，两者都无则报 `"流程缺少开始节点…"`。
5. 任一失败 → 400 + 中文 message。

### 完整 DSL 示例

```json
{
  "name": "示例流程",
  "threadCount": 2,
  "headers": [{ "name": "Accept", "value": "text/html", "description": "" }],
  "cookies": [],
  "variables": [{ "name": "startPage", "value": "1", "description": "" }],
  "nodes": [
    { "id": "n1", "shape": "start", "name": "开始", "x": 40, "y": 160,
      "params": [
        { "name": "loopCount", "value": "", "description": "循环集合/次数" },
        { "name": "loopVariableName", "value": "index", "description": "下标变量名" },
        { "name": "loopItemVariableName", "value": "item", "description": "元素变量名" },
        { "name": "loopParallel", "value": "0", "description": "迭代并行" },
        { "name": "urls", "value": "https://example.com/list?p=${startPage}", "description": "种子 URL" },
        { "name": "outputVariable", "value": "urlList", "description": "集合变量名" }
      ], "fields": [] },
    { "id": "n2", "shape": "request", "name": "抓取列表", "x": 240, "y": 160,
      "params": [
        { "name": "loopCount", "value": "${urlList}", "description": "循环集合/次数" },
        { "name": "loopVariableName", "value": "index", "description": "" },
        { "name": "loopItemVariableName", "value": "item", "description": "" },
        { "name": "loopParallel", "value": "0", "description": "" },
        { "name": "url", "value": "${item}", "description": "URL" },
        { "name": "method", "value": "GET", "description": "请求方法" },
        { "name": "outputVariable", "value": "resp", "description": "出参变量名" },
        { "name": "saveDocument", "value": "1", "description": "保留文档对象" }
      ], "fields": [] },
    { "id": "n3", "shape": "extract", "name": "抽取", "x": 440, "y": 160,
      "params": [
        { "name": "loopCount", "value": "", "description": "" },
        { "name": "loopVariableName", "value": "index", "description": "" },
        { "name": "loopItemVariableName", "value": "item", "description": "" },
        { "name": "loopParallel", "value": "0", "description": "" },
        { "name": "source", "value": "${respDoc}", "description": "抽取来源" },
        { "name": "mode", "value": "list", "description": "抽取模式" },
        { "name": "rowExpression", "value": "li.item", "description": "行选择器" },
        { "name": "outputVariable", "value": "rows", "description": "出参变量名" }
      ],
      "fields": [
        { "name": "title", "parserType": "CSS", "expression": "a", "attribute": "text", "attrName": "", "group": 1, "defaultValue": "" },
        { "name": "detailUrl", "parserType": "CSS", "expression": "a", "attribute": "attr", "attrName": "href", "group": 1, "defaultValue": "" }
      ] },
    { "id": "n4", "shape": "output", "name": "采集平台", "x": 640, "y": 160,
      "params": [
        { "name": "loopCount", "value": "${rows}", "description": "" },
        { "name": "loopVariableName", "value": "index", "description": "" },
        { "name": "loopItemVariableName", "value": "item", "description": "" },
        { "name": "loopParallel", "value": "0", "description": "" },
        { "name": "each", "value": "", "description": "逐条集合" },
        { "name": "cell:title", "value": "${item.title}", "description": "输出字段" },
        { "name": "cell:detailUrl", "value": "${item.detailUrl}", "description": "输出字段" },
        { "name": "key", "value": "${item.detailUrl}", "description": "业务主键" },
        { "name": "url", "value": "${item.detailUrl}", "description": "来源地址" }
      ], "fields": [] }
  ],
  "edges": [
    { "id": "e1", "from": "n1", "to": "n2", "condition": "", "exceptionFlow": "", "transmitVariable": true },
    { "id": "e2", "from": "n2", "to": "n3", "condition": "", "exceptionFlow": "2", "transmitVariable": true },
    { "id": "e3", "from": "n3", "to": "n4", "condition": "", "exceptionFlow": "", "transmitVariable": true }
  ]
}
```

---

## 12. 非分页 JSON 示例：`POST /collect/api/v2/collect/execution/run`

请求体 `{"collectFlowId":1,"persist":true,"variables":[{"name":"startPage","value":"3","description":""}]}`

```json
{
  "code": "00000", "msg": "操作成功", "ok": true, "success": true,
  "data": {
    "collectTaskId": 17, "collectTaskNo": "CT20260929101530123ABCD",
    "collectTaskFlowId": 1, "collectTaskFlowName": "示例流程",
    "collectTaskStatus": "RUNNING", "collectTaskTriggerType": "MANUAL",
    "collectTaskParams": "[{\"name\":\"startPage\",\"value\":\"3\",\"description\":\"\"}]",
    "collectTaskBeginTime": "2026-09-29 10:15:30", "collectTaskEndTime": null,
    "collectTaskCost": null, "collectTaskNodeCount": null, "collectTaskRequestCount": null,
    "collectTaskRecordCount": null, "collectTaskError": null,
    "collectTaskExecutorId": "hostA:12345", "running": true, "createName": null
  }
}
```

失败示例：`DELETE /collect/api/v2/collect/flow/delete?flowId=999` → **HTTP 400**

```json
{ "code": "C0400S0000", "msg": "流程不存在或已删除: 999", "data": null, "ok": false, "success": false }
```

---

## 13. 端点汇总

> **2026-09-26 校订**：原文写「10 个控制器、49 个 handler 方法」，并把已废弃的
> `CollectConsoleAuthController` 算在内，同时漏掉了 `Function` / `Notice` / `SchedulerConsole`
> 三个真实存在的控制器。按源码逐个统计（`@(Get|Post|Put|Delete)Mapping` 计数）实际为
> **12 个控制器、69 个 handler 方法**。

| 控制器 | 前缀 | 端点数 | 章节 |
|---|---|---|---|
| `CollectFlowController` | `/v2/collect/flow` | 9 | §1 |
| `CollectTaskController` | `/v2/collect/task` | 6 | §2 |
| `CollectExecutionController` | `/v2/collect/execution` | 5 | §3 |
| `CollectRecordController` | `/v2/collect/record` | 6 | §4 |
| `CollectFieldController` | `/v2/collect/field` | 2 | §5 |
| `CollectVariableController` | `/v2/collect/variable` | 4 | §6 |
| `CollectDatasourceController` | `/v2/collect/datasource` | 8 | §7 |
| `CollectCategoryController` | `/v2/collect/category` | 5 | §8 |
| `CollectDesignerController` | `/v2/collect/designer` | 3 | §9 |
| `CollectFunctionController` | `/v2/collect/function` | 5 | §10bis |
| `CollectNoticeController` | `/v2/collect/notice` | 6 | §10ter |
| `CollectSchedulerConsoleController` | `/v2/scheduler` | 12 | §10quater |
| **合计** | | **71** | |

分页接口共 **5 个**：`flow/page`、`task/page`、`record/page`、`scheduler/page`、`scheduler/log/page`
（后两个走 `PageResult` 单层结构，见 §0.5 的采集分页双层规则**不适用**——实测
`/v2/scheduler/page` 返回 `{code,data:{pageNo,pageSize,total,totalPages,data:[]}}`，
即 `data` 直接是分页体，与 `/v2/collect/**` 的双层不同）。

### 运行环境备注

- 默认端口 **18190**，context-path `/collect/api`。双节点本地验证常用 `18191`（`--plugin.job.role=SCHEDULER`）
  与 `18192`（`--plugin.job.role=EXECUTOR`）。
- 前端 `apps/vue-support-collect-starter` 的 vite 代理默认指向 **18190**，
  连非默认端口时用环境变量覆盖：`COLLECT_API_TARGET=http://127.0.0.1:18191 pnpm dev`。

