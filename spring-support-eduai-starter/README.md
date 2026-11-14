# spring-support-eduai-starter

教育 AI 集成模块（默认开启）：提供题库管理、AI 组卷、AI 阅卷（含扫描识别与 SSE 进度）、知识库语义检索、错题库、考试、以及汉字/成语/古诗/昆虫/词语等语文与自然素质教育语料能力。
依赖 MyBatis-Plus、Redis 缓存、消息队列与 AI 聚合管线，是一个功能型业务 starter，不是基础设施开关。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
|:-------|:-------|:-----|
| 自动配置 | `EduAiAutoConfiguration` | `@MapperScan` + `@ComponentScan` + `plugin.eduai.*` |
| 配置绑定 | `EduAiProperties` | `plugin.eduai.*` |
| Redis CacheManager `@Primary` | `EduAiCachePrimaryConfiguration` | Redis 源，TTL 3600s |
| 题库管理 | `EduQuestionController`（`IEduQuestionService`） | `/v2/edu/question` |
| 试卷管理 / AI 组卷 | `EduPaperController` | `/v2/edu/paper`（`/generate`、`/{id}/replace`、`/qr/{no}` 题项二维码反查） |
| 阅卷管理 | `EduMarkingController`（`IEduMarkingService`） | `/v2/edu/marking`（`/scan`、`/submit`、`/stream/{taskId}` SSE） |
| 阅卷异步队列 | `EduMarkingServiceImpl` / `MarkingMessageListener` | `edu.marking` 队列（queue-starter） |
| 知识点 / 知识库 | `EduKnowledgePointController` / `EduKnowledgeDocController` | `/v2/edu/knowledge-point`、`/v2/edu/knowledge-doc` |
| 错题库 | `EduWrongQuestionController` | `/v2/edu/wrongbook` |
| 考试管理 | `EduExamController` | `/v2/edu/exam` |
| 学生/教师中心 | `EduStudentCenterController` / `EduTeacherCenterController` | `/v2/edu/student`、`/v2/edu/teacher` |
| 维度字典（学科/年级/班级/学生/教师） | `EduDimensionController` / `EduStudentAvatarController` | `/v2/edu/dimension`、`/v2/edu/student-avatar` |
| 语文语料（汉字/成语/古诗/古诗游戏/词语/昆虫） | `EduHanziController` / `EduIdiomController` / `EduPoemController` / `EduPoemChallengeController` / `EduWordController` / `EduInsectController` | `/v2/edu/hanzi`、`/v2/edu/idiom`、`/v2/edu/poem`、`/v2/edu/poem-challenge`、`/v2/edu/word`、`/v2/edu/insect` |
| 应用配置项（管理员） | `EduAppConfigController` | `/v2/edu/app-config` |
| 趣味配置开关 | `EduFunConfigController` | `/v2/edu/fun` |
| 组卷模板 → 统一模板库同步 | `EduPaperTemplateSyncer` | 每 5 分钟（或页面「同步」按钮） |
| 学生/教师凭据与登录 | `EduCredentialConfiguration` + `StudentCredentialProvider` / `TeacherCredentialProvider` / 登录处理器 | `POST /v2/user/login`（oauth-client 扩展点） |
| 阅卷进度 SSE 推送 | `EduMarkingProgressHub` | `/v2/edu/marking/stream/{taskId}` |
| 服务信息登记 | `eduAiServiceInfoContributor` | 控制台模块能力 |
| 数据初始化 | `src/main/resources/db/init`（V2.1~V2.18） | 建表脚本 + 16MB 种子数据（含 20896 条幂等 INSERT） |

实际生效的能力：

- 整卷二维码识别：卷面页脚打印 `PAPER:{paperId}` 二维码（ZXing 解码），阅卷上传时自动归回内置卷，可追加 `;STUDENT:学号` 与 `;EXAM:考试ID`，身份冲突触发「替考告警」；
- 阅卷异步化：`marking.async=true` 时经 `edu.marking` 队列消费（queue-starter 的 `LocalMessageTemplate`），重启续跑 `resume-limit` 个未完成任务，`max-concurrent` 控制并发（默认 1 = 串行，防 ONNX 内存打满触发 OOM）；
- 任意卷识别策略：`extractStrategy` 走 ai-starter `AnyExtractStrategy` 扩展点，`whole`（整卷一次识别，默认）/ `region`（按版面区域分块，回退整卷）；
- 归属隔离：`EduUserContext#ownerScope()` 把「本用户 + 系统预置数据」过滤下推到 Mapper XML；无身份查询 fail-closed（永假值），管理员不过滤；
- 权限注解：维度管理、应用配置为管理员专属（类级 `@Permission`）；阅卷写操作仅 ADMIN/TEACHER；
- 组卷模板同步至统一模板库（template_info，来源 `paper`），来源失效行自动清理。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-eduai-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

关键依赖：

| 依赖 | 作用 |
|:-----|:-----|
| `spring-support-mybatis-starter` | MyBatis-Plus 基础（`ServiceImpl` / `BaseMapper`） |
| `spring-support-datasource-starter` | 数据源与动态路由 |
| `spring-support-redis-starter` + `redisson-spring-boot-starter:3.36.0` | Redis 缓存与 `CacheManager`（`EduAiCachePrimaryConfiguration` 复用 `CacheConfiguration.createRedisCacheManager`） |
| `spring-support-queue-starter` | 阅卷异步队列（`MessageTemplate` / `QueueListener`） |
| `spring-support-ai-starter` | `PaperAiService` / `PaperScanPipeline` / `PaperScanner` 阅卷与组卷 AI 管线 |
| `utils-support-deeplearning-starter` | ONNX 模型资产（OCR/版面/方向识别），部署需 `-Ddeeplearning.model.root-dir` |
| `spring-support-collect-starter` | 采集平台 `/v2/collect/**`（14 个 Controller + 流程图执行引擎），eduai 的「采集管理」收敛于此 |
| `com.google.zxing:core / javase:3.5.0` | 整卷二维码解码 |
| `spring-support-oauth-client-starter` | 鉴权注解与登录扩展点（`CredentialProvider` / `LoginHandler`） |
| `spring-support-account-starter:4.0.0.51` | 统一模板库（`TemplateService` / `SourceSyncer`） |
| `spring-support-osgi-starter` | OSGi 源码扩展点（学生凭据校验等 SPI 实现源码化，热改生效） |
| `spring-boot-starter-data-redis` | Spring Data Redis |
| `spring-support-job-starter` | 任务调度（MybatisJobRepository / JobMapper 扫描） |

父 POM 为 `spring-boot-starter-parent:4.1.0`，Java 25；`spring-support-account-starter` 例外显式钉 `4.0.0.51`。

## 三、配置项

配置前缀：**`plugin.eduai`**（`EduAiProperties`）。另外本模块还依赖多个框架级前缀（非本模块定义，见「六、其它」）。

### 3.1 阅卷 `plugin.eduai.marking.*`

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.eduai.marking.async` | `true` | 是否异步阅卷（投递 `edu.marking` 队列） |
| `plugin.eduai.marking.timeout` | `1800` | 单个阅卷任务的**总预算**（秒），超时中止并释放并发闸门；下限 30s（`Math.max(30, ...)`） |
| `plugin.eduai.marking.call-timeout` | `90` | 单次模型调用超时（秒）；经 `paperPipelineProperties` 映射给 ai-starter |
| `plugin.eduai.marking.max-score` | `5` | 单题最大分（兜底值） |
| `plugin.eduai.marking.wrong-score-rate` | `0.6` | 判错阈值：最终分/满分低于该比例即记入错题库；0 = 只有 0 分才算错 |
| `plugin.eduai.marking.image-dir` | `./data/marking` | 扫描阅卷图片持久化目录（异步任务可重启续跑） |
| `plugin.eduai.marking.resume-limit` | `50` | 启动时最多续跑未完成任务数 |
| `plugin.eduai.marking.max-concurrent` | `1` | 后台同时处理阅卷任务上限（默认串行，防 ONNX 内存打满） |
| `plugin.eduai.marking.preprocess` | `true` | 上传图片预处理（照度校正去阴影 + 灰度归一化）；经映射给 ai-starter |
| `plugin.eduai.marking.layout-correction` | `false` | ONNX 卡片/布局矫正（card-correction-detector），未检出自动回退原图；经映射给 ai-starter |
| `plugin.eduai.marking.extract-strategy` | `whole` | 任意卷识别策略：`whole`（整卷）/ `region`（分块，回退整卷）；经映射给 ai-starter |

### 3.2 组卷 `plugin.eduai.paper.*`

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.eduai.paper.max-total-score` | `100` | 试卷最高总分（读结算口：兜底逻辑，见 `PaperComposeSettingService#readPositiveInt(FIELD_MAX_TOTAL_SCORE, ...)`） |
| `plugin.eduai.paper.default-question-count` | `20` | 默认题目数量 |
| `plugin.eduai.paper.default-score-per-question` | `5` | 默认每题分值 |

组卷三项实际上是「AI 全局设置表 `sys_ai_global_setting` 的 `compose` 分组」→ 本配置兜底，不是纯配置文件生效。

### 3.3 知识库 `plugin.eduai.knowledge.*`

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.eduai.knowledge.vector-dim` | `768` | 向量维度；**仅声明，运行期未读取**（本模块主代码中没有读取点） |

### 3.4 AI 引擎 `plugin.eduai.ai.*`

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.eduai.ai.enabled` | `true` | 是否启用 AI（关闭后回退本地启发式）；经映射给 ai-starter 的 `PaperPipelineProperties.Ai.enabled` |
| `plugin.eduai.ai.provider` | 无 | AI 服务商；**仅声明，运行期未读取**（AI 客户端由 ai-starter 的 ChatClientRegistry / 聚合 bean 提供，配置走 AI 控制台 UI） |
| `plugin.eduai.ai.model` | 无 | 模型名；**仅声明，运行期未读取**（同上） |
| `plugin.eduai.ai.temperature` | `0.3` | 温度参数；**仅声明，运行期未读取**（同上） |
| `plugin.eduai.ai.retry.times` | `5` | 最大重试次数（0 = 不重试） |
| `plugin.eduai.ai.retry.backoff-millis` | `1000` | 初始退避毫秒 |
| `plugin.eduai.ai.retry.backoff-multiplier` | `2.0` | 退避倍数（<=1 按 2 处理） |
| `plugin.eduai.ai.retry.max-backoff-millis` | `20000` | 退避上限毫秒（<=0 按 15000 处理） |
| `plugin.eduai.ai.retry.retry-on-transient` | `true` | 除限流 429 外，是否重试瞬时故障（超时/502/503/连接异常） |

### 3.5 告警 `plugin.eduai.ai.alert.*`

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.eduai.ai.alert.enabled` | `false` | 是否启用告警（重试耗尽后 POST Webhook） |
| `plugin.eduai.ai.alert.webhook-url` | 无 | Webhook 地址（钉钉/企微/飞书标准卡片），配置后立即生效 |
| `plugin.eduai.ai.alert.timeout-millis` | `5000` | HTTP 请求超时毫秒（<=0 按 5000 处理） |

### 3.6 采集 `plugin.eduai.collect.*`

| key | 默认值 | 说明 |
|:----|:-------|:-----|
| `plugin.eduai.collect.default-cron` | `0 */30 * * * ?` | 采集任务未填 Cron 时的默认调度周期；**仅声明，运行期未读取**（采集已收敛到 collect-starter，其调度走 `plugin.collect` 前缀） |

### 3.7 框架级前置配置（非本模块定义）

本模块依赖的鉴权与数据源配置（application.yml 实测值）：

| key | 当前默认 | 说明 |
|:----|:-------|:-----|
| `plugin.oauth.enable` | `true` | oauth 鉴权开关 |
| `plugin.oauth.protocol` | `static` | 鉴权协议必须为 `static`（本地签发 TokenCipher/SM4 校验，`tcp` 会登录成功后列表全空） |
| `plugin.oauth.local-admin-fallback` | `false` | 必须保持 false（开启等价于「鉴权失败即内置 admin」越权漏洞） |
| `plugin.oauth.temp.user` | 空串 | 登录账号口令，部署方显式传入（`--plugin.oauth.temp.user='用户名:口令'`） |
| `plugin.datasource.script.enable` | `true` | 数据源脚本执行（`db/init` 下的 V* 脚本） |
| `plugin.datasource.script.scan-mode` | `ALWAYS` | 建表脚本每次启动扫描（按脚本名+版本记录去重执行） |
| `plugin.datasource.script.data-scan-mode` | `ONCE` | 种子数据只跑一次 |
| `plugin.queue.enable` / `plugin.queue.type` | `true` / `local` | 阅卷队列开关与本地实现 |
| `plugin.api.cors.enable` | — | 跨域白名单制（`allowed-origins` 为空不放行任何跨域来源） |

## 四、快速开始

**第一步：引入依赖并保证前置中间件**

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-eduai-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

需要 MySQL（主库 + `plugin.edu-auth.datasource` 第二数据源跟随主库）、Redis。AI 能力需要 ai-starter 的模型客户端（对话客户端经 AI 控制台 UI 落库 `sys_ai_global_setting` 配置）。

**第二步：配置 yaml（最小可用）**

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/eduai?useUnicode=true&characterEncoding=UTF-8&useSSL=false
    username: root
    password: ""
  data:
    redis:
      host: 127.0.0.1
      port: 6379
plugin:
  datasource:
    script:
      enable: true
      scan-mode: ALWAYS
      data-scan-mode: ONCE
  edu-auth:
    datasource:
      url: ${spring.datasource.url}
      username: ${spring.datasource.username}
      password: ${spring.datasource.password}
  oauth:
    enable: true
    protocol: static
    local-admin-fallback: false
    temp:
      user: "admin:change-me"
  queue:
    enable: true
    type: local
  eduai:
    marking:
      async: true
```

启动时 `db/init` 下的 V2.1~V2.18 脚本自动建表并灌入 16MB 种子数据（全幂等）。

**第三步：验证**

- 启动日志出现 `[EduAi]` 相关业务日志与控制台登记端点；`GET /actuator/health` 返回 UP；
- 登录：`POST /v2/user/login`（static 自签令牌），登录后带 `x-oauth-token` 访问：
  - `GET /v2/edu/question/page`（题库分页）
  - `POST /v2/edu/paper/generate`（AI 组卷，体带 `subject` / `knowledgePointIds` / `difficulty` 等）
  - `GET /v2/edu/marking/stream/{taskId}`（SSE 阅卷进度，需先 `POST /v2/edu/marking/scan` 上传图片拿 taskId）

## 五、运行示例

### 5.1 调用示例

**AI 组卷**：

```bash
curl -X POST http://127.0.0.1:19095/v2/edu/paper/generate \
  -H "Content-Type: application/json" \
  -H "x-oauth-token: <token>" \
  -d '{
    "subject": "数学", "difficulty": 3,
    "knowledgePointIds": [1, 2, 3],
    "typeConfigs": [{"type": "选择题", "count": 10, "score": 5}]
  }'
# 返回 paperId；再 GET /v2/edu/paper/{id} 查看详情
```

**扫描阅卷 + SSE 进度**（支持多张图片按页顺序，同名 `file` 字段重复出现）：

```bash
curl -X POST http://127.0.0.1:19095/v2/edu/marking/scan \
  -H "x-oauth-token: <token>" \
  -F "mode=SELF" -F "paperId=123" -F "studentId=S001" \
  -F "file=@page1.png" -F "file=@page2.png"
# 返回 taskId
curl -N http://127.0.0.1:19095/v2/edu/marking/stream/<taskId> -H "x-oauth-token: <token>"
# SSE：progress 帧逐阶段推送，终态 done 收流
```

**组卷模板同步**：`EduPaperTemplateSyncer` 实现 `SourceSyncer`（source=`paper`），register 到统一模板库；每 5 分钟全量修复一次并清理失效来源行。

### 5.2 SPI 扩展示例

本模块**实现并注册**两处 oauth 扩展点（不是定义新接口）：

| 扩展点 | 实现 | 注册名 |
|:-------|:-----|:-------|
| `com.chua.starter.oauth.client.support.credential.CredentialProvider` | `StudentCredentialProvider` / `TeacherCredentialProvider` | `student` / `teacher` |
| `com.chua.starter.system.handler.LoginHandler` | `StudentLoginHandler` / `TeacherLoginHandler` | `STUDENT` / `TEACHER`（大小写归一命中） |

注册方式双写：`ServiceProvider.register`（代码可见）+ `META-INF/extensions/com.chua.starter.system.handler.LoginHandler` 索引（provider 缓存是 SOFT 引用，重建后注册不丢）。登录口 `POST /v2/user/login` 按 `loginType-accountType` 组合查找——取不到处理器会抛空指针（对外 500），因此两类扩展点缺一不可。

要扩展（如新增一种账号来源），需同时提供 `CredentialProvider` 与 `LoginHandler` 实现并按上述两种方式注册；登录处理器必须无参可构造（登录口按类反射新建）。

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
|:-----|:-----|:-----|
| `spring-support-ai-starter` | 本模块依赖 | 阅卷/组卷 AI 管线；`EduAiAutoConfiguration#paperPipelineProperties` 把 `plugin.eduai.*` 映射给 ai-starter 的 `PaperPipelineProperties`（@Primary 覆盖其默认 Bean） |
| `spring-support-collect-starter` | 本模块依赖 | 题库「采集管理」已收敛到采集平台的 `/v2/collect/**`，eduai 的 `com.chua.starter.eduai.collect` 包不再提供 Controller（扫描仍声明，但包目录不存在） |
| `spring-support-datasource-hub-starter` | 可组合，无依赖 | 采集平台经其注册数据源；eduai 本身用 `spring-support-datasource-starter` 的单库 |
| `spring-support-oauth-client-starter` | 本模块依赖 | 鉴权注解 `@Permission` 与登录扩展点 |
| `spring-support-account-starter` | 本模块依赖（4.0.0.51） | 统一模板库，`EduPaperTemplateSyncer` 的目标 |
| `spring-support-osgi-starter` | 本模块依赖 | 学生凭据校验等 SPI 实现源码化（`./osgi-source` 目录热改生效） |
| `spring-support-common-starter` | 本模块依赖 | `ReturnResult` / `ServiceInfoContributor` / `@Permission` |
| `spring-support-eduai-starter` 与 `spring-support-ai-starter` | 易混淆，注意区分 | ai-starter 是通用 AI 管线基础设施（`/v2/ai` 对话、模型、用量、paper pipeline）；eduai 是面向教育业务的功能模块，聚合 ai-starter 并映射 `plugin.eduai.*` |

## 七、已知限制

- **`knowledge.vector-dim` 未读取**：`EduAiProperties.Knowledge#vectorDim` 有字段与默认值，但本模块主代码无读取点，配置不生效。
- **`ai.provider` / `ai.model` / `ai.temperature` 未读取**：AI 客户端由 ai-starter 的 `ChatClientRegistry` / 聚合 bean 提供，配置走 AI 控制台 UI（落库 `sys_ai_global_setting`）；`plugin.eduai.ai.*` 仅 `enabled` 与 `retry.*` 生效。
- **`collect.default-cron` 未读取**：采集调度已移交 collect-starter（`plugin.collect` 前缀），`plugin.eduai.collect.default-cron` 仅为兼容声明。
- **阅卷并发默认 1 串行**：`max-concurrent` 默认 1；调大会多路 ONNX 并发，内存不足会触发内核 OOM（实测 5 路并发 oom-kill 并形成崩溃循环）。
- **`marking.timeout` 是任务总预算**：与 `call-timeout` 分开计数；历史共用 300 秒曾导致 18 题卷在 17/18 处被整任务判失败。
- **无身份查询 fail-closed**：`EduUserContext.ownerScope()` 无身份返回永假值 `__anonymous__`，不泄漏数据；调度/队列等异步线程取不到身份，必须从父行继承归属。
- **登录扩展点注册失败即 500**：`POST /v2/user/login` 按 `loginType-accountType` 组合取 `LoginHandler`，取不到直接空指针（对外 500），两类扩展点缺一不可。
- **维度管理与应用配置为管理员专属**：`/v2/edu/dimension/**`、`/v2/edu/app-config/**` 类级 `@Permission`；学生令牌访问会 403。
- **整卷二维码是身份证据**：卷面 `PAPER:{paperId}` 二维码可携带 `STUDENT` / `EXAM`，二维码学生与提交学生不一致时拒绝并告警「替考」。
- **组件级红线下限**：`massive model assets`（ONNX ppocr/版面/方向）不随 maven 分发，部署需按 `utils-support-models-parent` 约定放入模型目录（`-Ddeeplearning.model.root-dir`，默认 `models/onnx`），否则阅卷模型不可用。
- **未提供事务/幂等保证的说明**：错题归位等写操作有 `@Transactional` 与 taskId 兜底，但「重新提交答题卡即可重试」依赖 queue-starter 的重试/死信策略，不属于本模块保证。