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

## 依赖即带入（R5）

新增 `xxx-starter` 依赖前，先确认对方的 `AutoConfiguration` 会往本服务塞什么 Controller / 定时任务 / 表迁移，是否真的需要。

## 交付闸门

`grep` 跨模块持久层引用 = 0；无反向依赖、无重复实现；`mvn -o -q compile -DskipTests` EXIT=0；编码 `BOM=0 / U+FFFD=0`。任一项不满足不得声称完成。

## SPI 扩展注册（易踩坑）

新增 `@Spi` 扩展实现（如 `GlobalSettingProvider`）且未标 `@AutoSpi` 时，必须手工在 `src/main/resources/META-INF/extensions/<SPI 接口全限定名>` 追加实现类全限定名。该文件是 git 跟踪的源码资源，编译期不会为纯 `@Spi` 类自动生成索引；漏加则编译绿但启动时静默不注册（如设置分组永不初始化）。

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

## 提交

只提交自己的文件，避开其它会话的在途改动；`_tmp_cleanup/` 不参与构建。
