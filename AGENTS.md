# AGENTS.md — spring-support-parent-starter

> **模块边界与依赖铁律：[`../规则.md`](../规则.md)（`D:\ch\project\规则.md`），动手前必须先读并遵守。违反 R1/R2 = S0 严重 bug。**
> 全局规则：`C:\Users\Administrator\.config\opencode\AGENTS.md`；源码编辑红线：`C:\Users\Administrator\.agents\GLOBAL-SOURCE-EDITING-RULES.md`。

## 本仓库定位

后端业务 starter 集合（`spring-support-*-starter`）。每个 starter 是一套**独立、业务完整**的能力，被依赖即带入完整功能。

## 三条铁律（摘要，全文以 `规则.md` 为准）

1. **R1 禁止业务穿插**：A 依赖 B，A 里不得再实现 B 的业务。判定：把 B 的 jar 删掉，A 里还有没有 B 的业务代码？有 → 违规。
2. **R2 禁止穿透持久层**：不得 `import` 其它业务模块的 `*.mapper`，不得直接读写别人的表；读走查询接口，写走命令接口。共享契约放 `spring-support-common-starter` 或更底层。
3. **R3 搬接口 ≠ 搬业务**：业务 = 规则判定 + 约束 + 级联 + 事务边界 + 事件发布 + 编排。

## 分层方向

`spring-support-api-*` → `spring-support-parent` → `utils-*`。优先复用底层工具；底层缺能力就下沉扩展，不在上层重造（重复实现 = S0）。

## 渠道管理口径（跨仓库硬约束）

- 渠道管理**整体**属于 `spring-support-account-starter`：表、实体、Mapper、渠道/模型/连接 CRUD、判重、级联、克隆、排序、归属隔离、凭据、模型开关、管理 API。
- `spring-support-ai-starter` 只是 `FreeChatClient` 的消费方：路由、探针、指标（`sys_ai_provider_metric`）、路由组、ONNX、协议探测属于 ai。
- 硬指标必须为 0：ai 引 `account.channel.mapper` / ai 写 `sys_account_channel*` / account 引 `com.chua.starter.ai.*`。
- 方向唯一 `account ← ai`，通过 `ChannelCatalogChangedEvent` 单向通知。
- 免登录生成类渠道（`image-*` / `video-*` / `model3d-*`）的能力由 common-starter 的 `KeylessMediaChannel` 契约声明。免登录生成模型（出图 flux / turbo、视频 doubao-video / qwen-video 等）由 account 模块启动时物化进 `sys_account_channel_model`（`KeylessGenerateModelMaterializer`，挂 `free-channel` 聚合渠道行下，capabilities 按渠道声明的任务类型标注）；生成目录（`/v2/ai/generate/models`）以库行为唯一来源，管理员在模型管理里启停即生效。ai 侧不得为它们补库或写渠道表；`GenerateClientResolver` 仅对「库里还没有行」的旧库保留 `free-channel` 兜底（出图 / 视频同口径），已有行的模型停用即拒。

## 发现服务优先（2026-11-28 定版）

- **地址解析只有一份实现**：`spring-support-discovery-starter` 的 `DiscoveryAddressResolver`（`discovery://<服务名>` 或裸服务名 → 发现中心实例地址列表；解析不到回落配置值，5 秒缓存，静态入口供 SPI 创建的实现取用）。其它模块不得再写第二套解析（R4）。
- **oauth**：`plugin.oauth.address` / `login-address` 支持 `discovery://<服务名>`／裸服务名（`AbstractProtocol#selectUrl`、`HtmlAdviceResolver` 两处消费）；未装配发现能力时与静态地址行为一致，认证服务端仍可独立配置。
- **sync**：已内建 `plugin.sync.client.discovery-name` / `scatter-id` 寻址；租户模块的 `client.discovery-name` 会串联到这里，`server-host` 允许写 `discovery://<服务名>`。
- **不装即降级**：发现未启用时 `DiscoveryConfiguration` 仍注册默认 `DiscoveryService`，解析器查不到实例就返回配置原值，不得因为发现中心抖动让鉴权/同步整体不可用。

## 依赖即带入（R5）

新增 `xxx-starter` 依赖前，先确认对方的 `AutoConfiguration` 会往本服务塞什么 Controller / 定时任务 / 表迁移，是否真的需要。

## 交付闸门

`grep` 跨模块持久层引用 = 0；无反向依赖、无重复实现；`mvn -o -q compile -DskipTests` EXIT=0；编码 `BOM=0 / U+FFFD=0`。任一项不满足不得声称完成。

## SPI 扩展注册（易踩坑）

新增 `@Spi` 扩展实现（如 `GlobalSettingProvider`）且未标 `@AutoSpi` 时，必须手工在 `src/main/resources/META-INF/extensions/<SPI 接口全限定名>` 追加实现类全限定名。该文件是 git 跟踪的源码资源，编译期不会为纯 `@Spi` 类自动生成索引；漏加则编译绿但启动时静默不注册（如设置分组永不初始化）。

索引口径（2026-11-26 全仓对账实证，704 个索引 / 1548 条实现行）：

- **索引文件名必须等于接口当前的 FQN**。接口随包结构迁移改了包名而索引留在旧文件名时，整份索引静默失效（`CustomServiceResolver` 按 `<接口 FQN>` 找文件，键不存在就连加载都不会发生）；同名旧键文件应改名而不是另存新文件。
- 索引行支持 `别名=FQN` 与裸 FQN；**SPI 名称会被规范化成大写**（`file_viewer` → `FILE_VIEWER`），探针按小写判存在会假红。
- 加载只做 `Class.forName` + `isAssignableFrom`，**不要求 `@Spi` 注解**；行内类名写错（类已改名）时该行被静默跳过，表现为「索引在、实现不在」。
- 实现类落在**接口包及其子包**下时可被 `SamePackageServiceResolver` 扫到，索引非必需；只有跨包实现才必须登记（datapool 的 nginx 实现属前者，框架侧不需要 nginx 索引）。
- 定期对账脚本 `temp/namespace-audit/index-audit.js`：判据 = 索引键接口存在 + 每行实现类存在，输出 `MOVED/MISSING` 计数。

可达性对账口径（2026-11-26 二轮，`temp/namespace-audit/spi-reachability-audit.js`）：

- 判「可达」必须同时认四条发现路径：**索引登记**、**同包扫描**（`SamePackageServiceResolver`，只扫接口包及其子包，跨包实现扫不到）、**Spring 托管**（`@Service`/`@Component` + 注入取用，不走 `ServiceProvider`）、`@AutoSpi` + 注解处理器生成（仅 utils 侧有处理器）。只按索引判会把同包/Spring 托管实现误判成缺失。
- 判「真隐患」要再加一条：**仓内存在 `ServiceProvider.of(<接口>.class)` 之类的真实消费点**。没有消费点的实现漏登记不产生行为差异（服务器实现类多由工厂按名/同包扫描创建），登记留档即可，不要为凑零而批量补索引。
- 同一接口的多个实现分散在多个模块时，索引文件放在**实现所在模块**即可——`CustomServiceResolver` 用 `classLoader.getResources()` 遍历，多 jar 同名索引会合并；不必集中到接口所在模块。
- 审计脚本自身两类假象要排除：嵌套实现/接口的键名是 `Outer$Inner`（按 `Outer.java` 定位会误报不存在），以及 `@SpiIgnore` 标记的类是有意不登记。

split-package 扫描口径（2026-11-26 修正，旧结论作废）：

- **模块粒度 = `src` 之前的完整相对路径**（`utils/utils-support-core-parent/utils-support-network-starter`），**不能取上层目录**。取父目录会把同一 parent 下的兄弟模块并成一个「模块」，兄弟模块之间的同包跨模块被误判为「同包同模块」，扫描结果假绿。
- 按错误粒度曾得出「SPLIT=0」；按正确粒度实测曾暴露 **4 处**，**2026-11-26 已全部整改归零**（复跑 `SPLIT_SCAN javaFiles=10340 packages=2272 SPLIT=0`）：
  - `com.chua.common.support.network.tcp`：`VertxTcpClient` → `com.chua.vertx.support.client`（接口 `TcpClient` 仍留 common）。
  - `com.chua.datalake.support.engine`：`HttpDatalakeQueryEngine` → `com.chua.datalake.support.query`（sink 侧 `DefaultPipelineEngine` 仍留原包）。
  - `com.chua.ffmpeg.support.processor`：`JavaCVFFmpegProcessor` → `com.chua.ffmpeg.support.processor.javacv`（它只 `implements FFmpegProcessor`，不继承 `AbstractFFmpegProcessor`）。
  - `com.chua.starter.service.configuration`：`ServiceConfiguration`、`CommonServiceSchemaInitializer` → `com.chua.starter.service`（configuration-starter 的 6 类不动，`SysSettingEnvironmentPostProcessor` 还登记在 `spring.factories`）。
- 迁移记录与运行时证据见 `D:\ch\验收报告\包结构残留寄宿迁移评估.md`、`D:\ch\验收报告\包结构整改.md`；扫描脚本 `temp/namespace-audit/split-module-level.js`。
- `@ComponentScan("...")`、`scanBasePackages = {"..."}`、`AutoConfiguration.imports` 里的包名/FQN 都是**字符串**，改包名时编译不报错，迁包必须逐条 grep 这些字面量。

按名取用与字符串 FQN 对账口径（2026-11-26 三轮，`temp/namespace-audit/spi-name-lookup-audit.js`、`fqn-string-audit.js`）：

- **`getExtension("名字")` 未命中不抛异常**，`DefaultServiceProvider` 静默回落 `getDefaultImpl()`（多数接口无默认实现 → `null`），所以「编译绿 + 无报错」证明不了按名可达。判据：实现 FQN 必须出现在该接口索引文件里，或同包扫描范围内，或调用方先走 Spring `getBeansOfType`；**不要按索引行文本反推运行期名字**（无别名时名字来自实现类 `@Spi`/`@Extension`/`@Component`）。
- 空名字 `getExtension("")` 走 `singleDefinition()` / `getDefaultImpl()`，属默认解析，别当按名缺陷统计。
- 字符串 FQN（`Class.forName`、`ServiceProvider.of(String)`、`SettingDefinition` 默认值、`spring.factories`、`*.imports`）在改包名时全部失联且编译无感。**`plugin.api.spi.mapping.<别名>` 的值必须是 SPI 接口 FQN（不是实现类）**——它被 `SpiOptionController` / `OptionalProvider` 直接交给 `ServiceProvider.of(String)`，写成不存在或旧包名的类时 `options()` 静默返回空列表，前端表现为 SPI 选项下拉为空。
- 审计出的 `Class.forName` 悬空项要先分层：`ClassUtils.isPresent` / `ReflectUtils.forName` + null 兜底的是**可选依赖钩子**（缺类即不注册，属设计），`instantiate` 后回落内存/文件实现的是**死兜底**，只有被当作真实类型直接使用、且无兜底路径的才是缺陷。

配置资源 FQN 对账口径（2026-11-27 四轮，`temp/namespace-audit/fqn-string-audit.js` + `exclude-probe.js`）：

- **`spring.autoconfigure.exclude` 写旧包名不会报错**：Spring Boot 的 `AutoConfigurationImportSelector#checkExcludedClasses` 先用 `ClassUtils.isPresent` 过滤，类不存在即静默丢弃——排除意图整体失效（实例：oauth 宿主 5 条 exclude 全是旧包名，AI 的 core/protocol/cache 自动配置一直在装配）。
- 反向更危险：**类存在但没登记进 `AutoConfiguration.imports`** 时写进 exclude，启动直接抛 `IllegalStateException: ... could not be excluded because they are not auto-configuration classes`。所以「把旧包名照字面改成新包名」可能把静默失效变成启动失败——先确认该类是否在 imports 里；被 `@ComponentScan` 扫到的 `@AutoConfiguration`（如 ai 的 `RagAutoConfiguration`、`AiCacheKeyGeneratorAliasConfiguration`）无法用 exclude 排除，也不需要排除。
- `META-INF/native-image/**` 的 `reflect-config.json` / `proxy-config.json` 同样是 FQN 字符串：只影响 native 构建（JVM 运行不读该文件），迁包后条目静默不命中、类被删除后成为过期条目；判定要求「简单名在某模块源码里有定义」，改包名前先 grep 类定义，别按相似度猜。

注解字符串与 imports 文件名对账口径（2026-11-27 五轮，`temp/namespace-audit/annotation-string-audit.js` + `autoconfig-imports-audit.js`）：

- 注解里的包名/类名（`@ComponentScan`、`@SpringBootApplication(scanBasePackages=...)`、`@MapperScan`、`@ConditionalOnClass(name=...)`、`@ImportResource`、AOP 切点里的类型）全是**字符串**，迁包/删模块时编译不报错。包不存在时组件扫描与 MyBatis mapper 扫描都是 **0 候选 + 只打 debug 日志**，能力整体消失而无告警；`@ConditionalOnClass(name="<包名>")` 写包名更是必失败（`ClassUtils.isPresent` 只认类）。
- **`META-INF/spring/*.imports` 的文件名本身也是易失字符串**：Boot 4.2 `ImportCandidates.LOCATION = "META-INF/spring/%s.imports"`、`AutoConfigurationImportSelector#getCandidateConfigurations` 用 `autoConfigurationAnnotation`（默认 `AutoConfiguration.class`）调用它 → 写成 `org.springframework.boot.autoconfigure.EnableAutoConfiguration.imports` **整份文件无人读取**，模块自动配置从未装载（实例：api 仓 achievement / signin 两模块，自 2026-11 建模块起就是坏的）。文件内容必须是**每行一个类 FQN**（`#` 可注释），写成 `spring.factories` 的 `Key=\` 续行格式时被读取方当成类名 → 只改文件名不改内容是「静默失效 → 启动失败」。
- **模块自足**：`@ComponentScan(本模块包)` + `@MapperScan(本模块 mapper 包)` 应写在本模块的自动配置类里（`SystemAutoConfiguration` / `SsoAutoConfiguration` / `ServiceConfiguration` 口径），不要让宿主补扫描包——宿主既不该知道模块内部包名，少写一处就等于整块能力消失。同一配置类既被 imports 登记又被 `@ComponentScan` 扫到时，Spring 会按类合并 `ConfigurationClass`，`@Bean` 不会重复注册（`SystemAutoConfiguration` 长期如此运行）。
- 审计脚本必须**先剥注释再匹配注解**：注释里出现 `@ConditionalOnClass` 会让后续括号匹配错位，实测产生 4 条 `CLS_MISSING` 假阳性（真值 0）。

## Javadoc 注释三坑（2026-11-25 实证）

- 注释体内**禁止出现字面 `*/`**（如 cron 表达式 `*/30` 会提前截断注释导致编译失败），须写成 HTML 实体 `*&#47;30`。
- 但 `{@code}` / `{@literal}` 体内**不解析 HTML 实体**（JDK 25 javadoc 实测原样输出 `&#47;`），实体方案只对普通 HTML 上下文（`<code>`、纯 `<pre>`）有效；含实体或裸 `<=` 的示例块应脱壳为普通 `<pre>`，并把 `<=` 转义为 `&lt;=`。
- 严格 javadoc 检查（-Xdoclint）跑整模块时：`-J-*` 启动器参数**不能进 @argfile**（放命令行）；@argfile 里超长单行 `-classpath`（约 59K / 数百 jar）会被 javadoc **静默丢弃**——全部第三方 import 报「程序包不存在」、100 错误上限中断，doclint 没跑到语法检查，结论是假阴性。可靠做法 = pathing jar（MANIFEST `Class-Path` 列全部 jar，`-classpath` 只放该 jar 一个短路径；样例 `temp/doc-verify/build-pathing.ps1`）。

## 行为探针验证套路与坑（2026-11-25 二阶段实证）

对 starter 文档做行为断言验证（真实 Spring 上下文，禁 mock）时的定版做法与坑：

- **Spring 7.0.10 已移除 `DefaultListableBeanFactory#setLazyInit`**：探针要全 Bean 懒加载时，用 `addBeanFactoryPostProcessor` 遍历全部 BeanDefinition 置 lazy（CCPP 之后、实例化之前执行，语义等价、版本无关）；样例 `temp/doc-verify/p0/src/docverify/p0/R.java` 的 `R.lazy(ctx)`。
- **pathing jar 的 manifest `Class-Path` 运行时 JVM 不解析**（javac 才解析；目录条目缺尾斜杠疑为主因）——javac 编译探针可用 pathing jar，`java` 运行时必须改用 **@argfile 直读 `target/cp.txt`**（`mvn -o dependency:build-classpath` 生成，反斜杠转正斜杠），顺带规避 32K 命令行限制。
- **@argfile classpath 必须把工作区相关模块 `target/classes` 前插**：maven 仓里的同 groupId 旧 jar 可能缺本会话新增的类（实例：common-starter 旧 jar 缺 `CollectAiInvoker`，导致 AiConfiguration 解析即失败）。
- **宽扫描配置类在普通 `AnnotationConfigApplicationContext` 下 refresh 必失败**：组件扫描面会拖入依赖 Web 作用域的宿主 Bean（如 `sysLoggerPointcutAdvisor` 注入 `HttpServletRequest`），BPP 注册阶段必炸。但**条件评估与 BeanDefinition 注册发生在更早的配置类解析阶段**——验证 `@ConditionalOn*` 装配语义时，catch refresh 失败后继续用 `ctx.getBeanFactory().containsBeanDefinition(...)` / `getBeanNamesForType(...)` 做定义级断言，结论依然有效；中断原因记 INFO 即可。
- **D3 类冲突验证**：消费方 `@Bean` 方法必须以冲突类型作**方法参数**（`@Bean Consumer c(AlipayClient client)`）由容器按类型解析，不能 `new Consumer(null)`；这样 `getBean(Consumer.class)` 才会真实抛 `NoUniqueBeanDefinitionException`。
- **DefaultAlipayClient 构造期校验密钥格式**：哑字符串触发「创建支付宝客户端失败」。探针现场 `KeyPairGenerator` 生成 RSA 密钥对（PKCS8/X.509 Base64）填 `private-key`/`alipay-public-key`——这是合法生成的测试密钥，不是 mock。
- **真实产品缺陷（待修复）**：`AliyunAlipayProperties.keepAliveDuration` 默认 300000ms，超出支付宝 SDK `AbstractAlipayClient.setKeepAliveDuration` 的 60s 上限（`AlipayApiException: 连接超时时间不能超过60s`）——`plugin.aliyun.alipay.enabled=true` 且不显式调低该值时 `alipayClient` Bean 必然创建失败。修法：默认值 ≤60000 或 factory 内钳制。
- PS 5.1 驱动探针：`Start-Process` 后 `$proc.ExitCode` 常为空（只判 EXITED/TIMEOUT）；`*>`/`2>&1` 重定向 native 进程输出写成 UTF-16LE（读日志先验 BOM，或用 `-RedirectStandardOutput` 参数）；javac 编译探针必须带依赖 classpath（`classes;<模块>.jar`）。

- **本地仓上游制品陈旧会伪装成编译错误（2026-11-27 实证）**：`mvn -pl <模块>` 不带 `-am` 时上游依赖取 `D:\maven-repo` 里的 jar；该 jar 比上游源码旧，下游就报「找不到符号」——实例：`eduai` 报 `PaperAiService.QuestionDraft`（嵌套类）/ `generateQuestionDrafts(...)` / `MarkingOutcome.isUnavailable()` 缺失，真因是 `spring-support-ai-starter` 制品停在 11-21，而这三个符号是之后才入源码的。判定：① 报错符号所在文件 `git status` 干净且在 HEAD；② 比对制品 mtime 与上游提交时间；③ 用类字节核对符号（嵌套类条目名是 `PaperAiService$QuestionDraft.class`；`isUnavailable` 由 `@lombok.Data` 生成，在 `PaperAiService$MarkingOutcome.class` 里，主类里搜不到）。修法：先 `mvn -o install -DskipTests -pl <上游模块>` 再装下游；**不要改下游源码去迁就旧 jar**。

## 数据同步（datasync）业务归属

- 同步任务（`ms_sync_task` / `ms_sync_task_run_log`）、`SyncTaskController`/`SyncTaskService`、`support/datasync` 引擎（源读取、字段映射、Sink、CSV 直灌 loader）、`Sync*`/`Bulk*` VO/DTO 全部属于 `spring-support-datapool-starter`；monitor 只依赖它并做宿主装配，不得在自己模块内实现同步业务。
- 端点路径沿用前端契约 `/ms/monitor/datasync`、`/ms/monitor/bulkimport`（`BulkImportController` 类级 `/ms/monitor`），迁移模块不改 URL。
- 引擎的 `JdbcCsvLoader`（`mysql` / `postgresql`）走 `META-INF/extensions/<接口 FQN>` 注册；迁包时索引文件名与行内实现 FQN 必须同改，否则整份索引静默失效。

## datapool 中间件客户端口径（2026-11-28 定版）

- **datapool 只留契约消费方，不留客户端实现**：Controller / Service 接口 / VO / 纯展示逻辑在 datapool；ZooKeeper、MongoDB、SkyWalking 的实现分别落 `utils-support-zookeeper-starter`、`utils-support-mongodb-starter`、`utils-support-skywalking-starter`，经 `ServiceProvider` 按名取用（`zookeeper` / `mongodb` / `skywalking`）。
- **契约落点**：ZK/Mongo 契约（`*ManageProvider` + record）在 `utils-support-datasource-starter` 的 `com.chua.datasource.support.middleware.{zookeeper,mongodb}`；SkyWalking 复用 `utils-support-common-starter` 的 `OtnlEngine` + `OtnlBackend`（`configure` 是默认方法，地址变更由实现作废自己的缓存）。
- **不装即降级**：`ServiceProvider.getExtension` 未命中静默回落 `getDefaultImpl()`（通常 null），取用方必须判 null 并抛 `MiddlewareNotInstalledException`，控制层转「功能未开放」的业务错误，不能抛 500、也不能给出演示数据。
- **datapool 不声明这三个实现**，按需引入或 OSGi 后装；因此 8082 宿主上 ZK/Mongo/SkyWalking 页面默认提示未开放属预期，要开就装对应 starter。
- **`spring-support-datasource-starter` 的文件库引擎适配（`H2EngineAutoConfiguration` / `SqliteEngine*Endpoint`）依赖 `utils-support-h2-starter` / `utils-support-sqlite-starter`，必须声明为 `provided`**（与 druid/hikari/seata 同口径）：`compile` 会把 H2/SQLite 传递给所有消费方，违反「生产模块不携带 H2、SQLite」。

## datasearch 业务归属与冗余判定（2026-11-28）

- **`spring-support-datasearch-starter` 保留，不冗余**：它提供 `/v2/weather`、`/v2/holiday`、`/v2/exchange-rate`、`/v2/ip`、`/v2/location`、`/v2/typhoon` 六组公共数据接口，以及 `CreditQueryProvider` SPI 和对应 provider；account 等模块有 116 处对其 credit 契约/模型的真实引用，删除会直接破坏余额试查能力。
- **它与 `DbConsoleController` 不是重复实现**：数据库 Web 管理器归 `spring-support-datapool-starter`（`/ms/monitor/db`），datasearch 不查业务库、不承载 SQL 控制台；两者只是名称相近。
- monitor 的 `DatasearchConflictResolve` 仅用于「monitor 同时装入 datasearch 与 common-service/common-system 历史控制器」时消解 `/v2/holiday/get`、`/v2/ip/lookup` 的重复映射；单独的历史控制器宿主仍依赖原实现，不能为消除重复而整体删除。

## 提交

只提交自己的文件，避开其它会话的在途改动；`_tmp_cleanup/` 不参与构建。
