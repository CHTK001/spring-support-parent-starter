# spring-support-ai-starter

把 AI 能力以 Spring Boot Starter 形式装配进宿主：对话 / Agent / 图像 / OCR / 版面 / 人脸 / 重识别 / 文生图 / 试卷 / RAG / 用量 / 协议网关。配置来自「数据库（页面）+ YAML 覆盖」，各能力客户端按配置**懒构建并缓存**，配置写入后通过事件重建。

> 本 README 描述的是**本 starter 的 Spring 集成面**（自动装配、Bean、缓存、配置优先级、扩展点）。
> `AiChat` / `AiService` 等链式图像 API 属于底层 `utils-support-deeplearning-starter`，见该模块文档。

## 一、模块能力

### 1.1 自动装配

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 共 7 个：

| AutoConfiguration | 触发条件 | 注册什么 |
|---|---|---|
| `AiConfiguration` | 无条件 | `@ComponentScan com.chua.starter.ai.support` + `@MapperScan`；`yamlChatClient`、`defaultChatClient`、`ChatClientRegistry`、`AgentRegistry`、`AiDialogRecordService`、`ServiceInfoContributor`；`ApplicationReadyEvent` 时收集并登记所有 `ChatClient`（`AgentProviderFactory` 等由组件扫描装配） |
| `AiGatewaySecurityAutoConfiguration` | 无条件 | 协议网关的 6 个过滤器 + 限流器（健康探针 → IP ACL → 包体大小 → QPM 限流 → 并发预算 → 首字节超时），每个过滤器内部读自己的 `security_*_enabled` |
| `AiGenerateAutoConfiguration` | `plugin.ai.generate.enabled=true`（默认 true）且 classpath 有 `ImageClient` | 扫描 `controller`，装配文生图 / 文生视频接口 |
| `AiProtocolServerConfiguration` | `plugin.protocol.enable=true` | `aiProtocolServerFilters`：OpenAI / Claude / Gemini 三协议端点；同时要求 `runtime.protocol_server_enabled` 为真 |
| `UsageAutoConfiguration` | `plugin.ai.usage.enabled=true`（默认 true）且 classpath 有 `SysAiUsageMapper` | `SysAiUsageService`、`SysAiPricingService`、`UsageRecorderInterceptor`、`UsageAggregationTask`，并开启 `@EnableScheduling` / `@EnableAsync` |
| `AiCacheAutoConfiguration` | `chua.ai.cache.enabled=true`（默认 true） | `aiCacheManager`（Caffeine 本地）+ Redis 缓存管理器（`redis-cache--1` 永久、`redis-cache-600` 短 TTL） |
| `AiActuatorAutoConfiguration` | `chua.ai.actuator.enabled=true`（默认 true）且 classpath 有 Actuator | `ai-config` / `ai-models` / `ai-beans` / `ai-cache` 四个只读端点 |

> `ai-starter.yml` **不能改名成 `application.yml`**：它是示例应用的配置模板，只由 `AiStarterApplication` 通过 `spring.config.import` 导入；改名会整份替换宿主的配置。

### 1.2 核心 Bean

| Bean（类型） | 作用 | 备注 |
|---|---|---|
| `defaultChatClient`（`ChatClient`） | 默认对话客户端：SPI 发现 + 容器内 `ChatClient` 聚合为 failover 客户端 | `@ConditionalOnMissingBean`：容器里已有其它 `ChatClient` 时不创建 |
| `yamlChatClient`（`ChatClient`） | 按 `plugin.ai.chat.*` 装配的固定客户端 | 只在 `enabled=true` 且 `app-key` 非空时创建；先于 `defaultChatClient` 注册 |
| `chatClientRegistry`（`ChatClientRegistry`） | provider / model / capability 三套索引 | `getByProvider` / `getByModel` / `getByCapability` / `getDefault` / `getAggregate` |
| `agentProviderFactory`（`AgentProviderFactory`） | 按 `runtime.agent_impl` 创建 Agent 引擎 | 支持 `opencode-server`（默认）与 `agentscope`；前者不可用时自动降级，`opencode` 作为历史别名归一 |
| `agentRegistry`（`AgentRegistry`） | Agent 模型登记与查询 | 没有可直接注入的 `Agent` Bean，按会话/引擎从工厂取 |
| `AiChatFacadeService` | 统一对话门面：`call` / `callForResult` / `probeChatClient` / `stream*`，含限流、用量计量、别名解析 | 业务侧首选入口 |
| `aiDialogRecordService`（`AiDialogRecordService`） | 对话记录落库 | — |
| `ModelClientResolver` | `provider::model` → `ChatClient` 解析 + 5 分钟 TTL 缓存 | 供门面/Agent 内部使用，也可直接注入 |
| `aiServiceInfoContributor` | `/actuator/info` 暴露 AI 能力摘要 | — |

| Bean（类型） | 作用 | 备注 |
|---|---|---|
| `GlobalSettingReader`（接口） | 统一读生效值：YAML > 用户覆盖 > 全局 | `getStr` 走 Redis 缓存；`getStrDirect` **绕过缓存**直连 DB，模型解析用它 |
| `SysAiGlobalSettingService` | 全局配置读写 | `getValue` 有 Redis 缓存；写后清键并发布事件 |
| `SysAiUserSettingService` | 用户覆盖层读写 | 读合并视图、写覆盖、清覆盖；同样带缓存与失效 |
| `DefaultModelResolver` | 默认模型解析（`chat()` / `resolve(key)` / `resolveAll()`） | 本地缓存 + `SettingChangedEvent` 失效；页面值优先于 `plugin.ai.default-model.*` |

| Bean（类型） | 作用 | 缓存方式 |
|---|---|---|
| `OcrClientManager` | OCR 管线：检测 / 识别 / 方向矫正 / 文本矫正 / 超分；`recognize` / `recognizeDetail` | `volatile OcrPipeline` 懒构建，`OcrSettingsChangedEvent` 重建 |
| `FaceClientManager` | 人脸管线、特征、活体、质量、属性/表情/关键点/换脸检测、超分/修复 | 各自 `volatile` 字段懒构建，`FaceSettingsChangedEvent` 重建 |
| `ReidClientManager` | 重识别管线、轨迹、低阈值检测器；`currentPipeline` / `newTrack` | `volatile` + 事件重建；按账号重建入口在 `ReidStores` |
| `ImageRecognitionClientManager` | 分类 TopK、目标检测、图像描述、超分 | `volatile` 客户端 + `fingerprint`，配置一变就重建 |
| `ColorizeClientManager` | 图像上色 `ImageEnhancer` | `enhancerCache` + `fingerprint` |
| `LayoutClientManager` | 版面检测 | `volatile LayoutDetector` + `fingerprint` |
| `PaperClientManager`（实现 `PaperClient`） | 试卷识别：版面 + OCR + LLM 结构化 | 监听 `PaperSettingsChangedEvent` / `OcrSettingsChangedEvent` 重建 |
| `RagClientManager`（实现 common 的 `RagClientProvider`） | 按用户装配 RAG 客户端；`current()` / `currentPipeline()` / `diagnostics()` | `Map<userId, RagClient>` + 内部 `ChatClient` 缓存；`RagSettingsChangedEvent` 重建 |
| `GenerateService` | 文生图 / 文生视频任务：`create` / `query` / `cancel` / `delete*` | 客户端由 `GenerateClientResolver` 按 provider 缓存并可 `invalidate(providerId)` |
| `ChatSkillSupport` | 技能/工具装配与失效 | 提供 `invalidate()`，技能增删后调用 |
| `McpConnectionService` | MCP 连接与目录管理 | — |
| `UsageRecorderInterceptor` / `UsageAggregationTask` / `SysAiUsageService` / `SysAiPricingService` | 用量记录、定时聚合、查询与计价 | 均由 `UsageAutoConfiguration` 注册（后两者 `@ConditionalOnMissingBean`） |

### 1.3 网关安全与协议

| Bean | 作用 |
|---|---|
| `gatewayRateLimiter` | 限流器（本地滑动窗口 / Redis 滑动窗口） |
| `aiGatewayHealthCheckFilter` | 健康探针直接 200，不进业务 |
| `aiGatewayIpFilter` | IP ACL |
| `aiGatewayBodySizeFilter` | 按 `Content-Length` 拦超大包 |
| `aiGatewayRateLimitFilter` | AppKey / IP / Path 三维 QPM |
| `aiGatewayConnectionBudgetFilter` | 在途连接预算 |
| `aiGatewayRequestTimeoutFilter` | 首字节超时 |
| `aiProtocolServerFilters`（`List<AiProtocolServerFilter>`） | OpenAI / Claude / Gemini 协议入口；`plugin.protocol.enable` 与 `runtime.protocol_server_enabled` 都为真才生效 |

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-ai-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

运行时需要的资源（不是 Maven 依赖，但缺了会直接体现在行为上）：

| 依赖 | 用途 | 缺失时 |
|---|---|---|
| MySQL | `sys_ai_*` 配置 / 会话 / 用量 / 任务表（`src/main/resources/db/init/V5.1__init_all_ai.sql` 及 `V5.x` 增量） | 启动失败 |
| Redis | 设置缓存、用量时间轮、部分限流 | **可降级**：缓存读写失败只打 WARN（见 `CacheResilienceConfiguration`） |
| 本地模型目录 | ONNX 供应商按 `<模型目录>/<modelId>/model.onnx` 读取 | 对应能力报「未配置」 |
| Actuator | `ai-*` 只读端点 | 端点不注册，不影响对话 |

关键编译期依赖：`spring-support-common-starter`（`ServiceInfoContributor` 等）、`spring-support-datasearch-starter`（`UsageParser`、`ModelMetricsProvider`、`UsagePricingReconciler` 契约）、`utils-support-deeplearning-starter`（`ChatClient` / `ImageClient` / OCR / 人脸等底层实现）、MyBatis-Plus 与对应 mapper。

## 三、配置项

### 3.1 YAML 配置

| key | 默认值 | 说明 |
|---|---|---|
| `plugin.ai.chat.enabled` | `false` | 是否装配 YAML 声明的对话客户端 |
| `plugin.ai.chat.provider` | 空（回落 `openai`） | 厂商标识，对应 `ChatClient` SPI 名 |
| `plugin.ai.chat.base-url` | 空（用实现内置地址） | API 基地址 |
| `plugin.ai.chat.app-key` | 空 | 密钥；为空时不创建 `yamlChatClient` |
| `plugin.ai.chat.app-secret` | 空 | 部分厂商需要 |
| `plugin.ai.chat.model` | 空 | 模型名 |
| `plugin.ai.chat.temperature` / `max-tokens` / `timeout-millis` | 空 | 采样与超时；为空走实现默认 |
| `plugin.ai.chat.auth-scheme` / `auth-header` / `auth-prefix` / `proxy` | 空 | 自定义鉴权头与代理 |
| `plugin.ai.default-model.*` | 空 | 页面值为空时的兜底；字段含 `chat`、`ocr-detect`、`ocr-recognize`、`layout`、`text-to-image`、`rag-embedding` 等 32 项 |
| `plugin.ai.generate.enabled` | `true` | 文生图 / 文生视频接口开关（另需 classpath 有 `ImageClient`） |
| `plugin.ai.generate.image.*` / `plugin.ai.generate.video.*` | `provider=openai`、`model=dall-e-3`/`sora` | 生成默认参数 |
| `plugin.ai.usage.enabled` | `true` | 用量模块（另需 `SysAiUsageMapper` 在 classpath） |
| `plugin.protocol.enable` | `false` | 协议网关端点（部署级开关，改了要重启） |
| `chua.ai.cache.enabled` | `true` | 声明式缓存与 `aiCacheManager` |
| `chua.ai.actuator.enabled` | `true` | 四个 Actuator 端点总开关 |

示例：

```yaml
plugin:
  ai:
    chat:
      enabled: true
      provider: zhipu
      base-url: https://open.bigmodel.cn/api/paas/v4
      app-key: ${AI_CHAT_APP_KEY:}
      model: glm-4-flash
      temperature: 0.7
      max-tokens: 4096
      timeout-millis: 60000

    default-model:
      chat: gpt-4.1-mini
      ocr-detect: onnx:paddleocrv6-det
      ocr-recognize: onnx:paddleocrv6-rec
      layout: onnx:pp-doc-layout

    generate:
      enabled: true
    usage:
      enabled: true

  protocol:
    enable: true

chua:
  ai:
    cache:
      enabled: true
    actuator:
      enabled: true
```

### 3.2 配置优先级（从高到低）

| 层级 | 位置 | 说明 |
|---|---|---|
| 1. YAML 钦定 | `plugin.ai.settings.<group>.<field>` | 部署级覆盖，只读；改了要重启 |
| 2. 用户覆盖 | `sys_ai_user_setting`（按 `userId`） | 页面上普通用户/管理员自己的值 |
| 3. 全局默认 | `sys_ai_global_setting` | 页面「配置中心」保存的全局值 |
| 4. 代码默认 | `GlobalSettingProvider.SettingItem.defaultValue` | 各 `*GlobalSettingProvider` 声明 |

### 3.3 配置分组（SPI 声明，共 24 组）

`runtime`、`session`、`image`、`ocr`、`face`、`layout`、`image_search`、`rag`、`paper`、`marking`、`compose`、`bot`、`asr`、`tts`、`search`、`memory`、`generate`、`input`、`input_suggest`、`global`、`channel_probe`、`security`、`health`、`sim`。

分组由 `@Spi("<group>")` 的 `GlobalSettingProvider` 实现声明，启动期由 `GlobalSettingDefinitions` 预热，经 `GET /v2/global-setting-definitions` 下发给设置页。

## 四、快速开始

**第一步**：准备 MySQL（先跑 `V5.1__init_all_ai.sql`）与 Redis（可降级），引入本模块。

**第二步**：至少配一个可用的对话客户端——YAML 声明或页面配置二选一：

```yaml
plugin:
  ai:
    chat:
      enabled: true
      provider: openai
      app-key: ${AI_CHAT_APP_KEY}
      model: gpt-4.1-mini
      base-url: https://api.openai.com/v1
```

**第三步**：注入 `AiChatFacadeService` 发起一次对话；确认 `/actuator/ai-models` 里能看到该模型，`/v2/global-setting-definitions` 返回非空 `data`。

## 五、运行示例

### 5.1 调用示例

本模块无链式 API：`AiChatRequest` 是 Lombok `@Data`（setter 式），门面方法直接返回结果值；只有底层的 `ChatClientSetting`（utils 层）才有 Builder。以下为步骤式用法。

**同步强类型调用（推荐）**：

```java
@Autowired
private AiChatFacadeService chatFacade;

AiChatRequest req = new AiChatRequest();
req.setPrompt("把这段需求拆成任务清单");
req.setSystemPrompt("你是资深架构师");
req.setModel("gpt-4.1-mini");
req.setTemperature(0.3);

AiChatResult result = chatFacade.callForResult(req);
if (result.success()) {
    System.out.println(result.text());     // 正文
    System.out.println(result.usage());    // 模型回传用量
} else {
    System.out.println(result.errorMessage());
}
```

**返回 `Map` 的通用入口**：`chatFacade.call(req)`，适合前端直传的场景。

**探测可用性（不发网络请求）**：

```java
AiClientProbe probe = chatFacade.probeChatClient("gpt-4.1-mini");
```

**流式（SSE）**：`stream(...)` 返回 `SseEmitter`；需要真实用量、执行轨迹、工具调用、人工确认时用 `streamWithUsage(...)` 的重载：

```java
SseEmitter emitter = chatFacade.streamWithUsage(
        req,
        chunk -> { /* 增量正文 */ },
        (reply, usage) -> { /* 完成：完整回复 + 真实用量 */ },
        err -> { /* 全链路失败 */ },
        thinking -> { /* 深度思考增量 */ });
```

**视觉 / OCR**：

```java
@Autowired private OcrClientManager ocr;
@Autowired private ImageRecognitionClientManager vision;

List<OcrResult> blocks = ocr.recognizeDetail(imageBytes);
List<DetectionInfo> faces = vision.detect(imageBytes);
```

**默认模型**：

```java
@Autowired private DefaultModelResolver modelResolver;

String chatModel = modelResolver.chat();            // 页面 > plugin.ai.default-model.chat
String ocrModel  = modelResolver.resolve("ocr");    // 按能力键解析
```

### 5.2 SPI 扩展示例

本模块的扩展点都在 `META-INF/extensions/` 下，登记格式为 `名称=实现类全限定名`（或一行一个类名）。

| 扩展点 | 做法 | 生效点 |
|---|---|---|
| `com.chua.common.support.ai.chat.ChatClient` | 实现 `ChatClient`，用 `@Spi("<provider>")` 登记；`ChatClient.create(setting)` 按 provider 取实现 | `ApplicationReadyEvent` 时被登记进 `ChatClientRegistry`，`defaultChatClient` 自动聚合为 failover |
| `com.chua.common.support.ai.rag.GlobalSettingProvider` | 实现 `GlobalSettingProvider` 并 `@Spi("<group>")` 声明分组与新字段 | `GlobalSettingDefinitions` 启动预热，`/v2/global-setting-definitions` 与设置页自动出现 |
| `com.chua.common.support.datasearch.usage.spi.UsageParser` | 实现 `UsageParser`（Spring Bean 或 SPI 实现均可） | 名称出现在「用量配置 → Provider 白名单」；`UsageAggregationTask` 定时拉取 |
| `AgentProvider` | 由 `AgentProviderFactory` 按 `runtime.agent_impl` 选择 | 不可用时降级到 `agentscope` 并在设置页显示原因 |

**示例一：新增一个对话客户端**

```java
@Spi("my-llm")
public class MyChatClient implements ChatClient {

    private final ChatClientSetting setting;

    public MyChatClient(ChatClientSetting setting) {   // SPI 按 (name, setting) 实例化
        this.setting = setting;
    }

    /** 唯一的抽象方法：返回正文；其余能力都有 default 实现 */
    @Override
    public String chatSync(String prompt) {
        return callMyGateway(setting.getBaseUrl(), setting.getApiKey(), prompt);
    }
}
```

登记（`META-INF/extensions/com.chua.common.support.ai.chat.ChatClient`）：

```text
my-llm=com.example.ai.MyChatClient
```

之后 YAML 写 `plugin.ai.chat.provider: my-llm`，或页面里把渠道 provider 配成 `my-llm`，即可解析到该实现（`ChatClient.create` 找不到时，只要给了 `base-url` 还会按 OpenAI 兼容协议兜底）。

**示例二：新增一个配置分组**

```java
@Spi("my_group")
public class MyGlobalSettingProvider implements GlobalSettingProvider {

    @Override
    public String group() { return "my_group"; }

    @Override
    public String groupName() { return "我的分组"; }

    @Override
    public List<SettingItem> getItems() {
        // 字段名 / 默认值 / 值类型(STRING|NUMBER|BOOLEAN) / 描述 / 排序
        return List.of(new SettingItem("my_model", "", "STRING", "默认模型", 10));
    }
}
```

登记到 `META-INF/extensions/com.chua.common.support.ai.rag.GlobalSettingProvider` 后，启动预热即收录，`/actuator/ai-config` 的 `groupCount` 与 `groups` 会多出该组。

## 六、与其它 starter 的关系

- **底层能力**：`utils-support-deeplearning-starter` 提供 `ChatClient`、`ImageClient`、OCR / 人脸 / 版面等实现；本模块只做 Spring 装配与配置分发，不重复定义这些类型。
- **`spring-support-datasearch-starter`**：提供 `UsageParser`、`ModelMetricsProvider`、`UsagePricingReconciler` 契约，用量同步与计价对账依赖它（因此会同时带上 `/v2/**` 顶栏接口）。
- **`spring-support-monitor-starter`**：反向依赖本模块，用 `AiChatFacadeService` 做主机指标 / 告警 / Redis 的智能分析（`/ms/monitor/ai/*`）。
- **`spring-support-account-starter`**：渠道账号、AppKey、Cookie 池；本模块通过 `AccountChannelAuthManager` / `ChannelCreditProvider` SPI 接入具体渠道。
- **`spring-support-common-starter`**：`ServiceInfoContributor`、`IptablesService`、`CacheConstant`（`REDIS_CACHE_ALWAYS` → Bean 名 `redis-cache--1`）。
- **`spring-support-cache-starter` / `spring-support-redis-starter`**：宿主若已启用多级缓存，本模块的 `aiCacheManager` 按名共存，**刻意不标 `@Primary`**，避免与框架主缓存管理器冲突。

## 七、已知限制

1. **MySQL 是硬依赖**：`AiConfiguration` 无条件做 `@MapperScan` 并注册大量 `sys_ai_*` 服务，没有库会启动失败；只想用 `ChatClient` 的宿主也绕不开。
2. **Redis 可降级但不共享**：`CacheResilienceConfiguration` 让缓存读写失败只打 WARN，写入仍成功；代价是多实例之间不共享失效，设置改动可能延迟生效。
3. **`plugin.ai.chat` 默认关闭**：`enabled=false` 时不创建 `yamlChatClient`；开启但 `app-key` 为空同样不创建（避免「能力显示可用、调用必然 401」）。
4. **YAML 与页面是两层**：`plugin.ai.chat.*` 只影响 `yamlChatClient`；页面/库里的渠道配置走 `ModelClientResolver`，两者不是同一份配置。
5. **直接改库不会失效缓存**：设置缓存键为 `sys:ai:global-setting:<group>:<field>`，只有走接口/页面保存才会清键。模型解析链路用 `getStrDirect` 绕过缓存，所以模型改了看起来「立刻生效」，其它分组不会。
6. **协议端点要两级开关**：`plugin.protocol.enable=true`（YAML，重启生效）**且** `runtime.protocol_server_enabled=true`（设置表）；后者读取异常时按关闭处理。
7. **`protocol_server_enabled` 改了也要重启**：过滤器在启动时装配成 Bean，运行期改值不会摘掉已注册的端点。
8. **Agent 引擎可能降级**：默认 `opencode-server`，可执行文件缺失且自动下载关闭/失败时降级到 `agentscope`，能力边界不同（工具链、MCP、规划模式受影响）。
9. **Actuator 端点默认开启**：`chua.ai.actuator.enabled` 缺省为 `true`，对外暴露前请确认 `management.endpoints.web.exposure.include` 的范围；`ai-config` 里凭据字段统一 `***`。
10. **`/v2/global-setting-definitions` 返回空 = SPI 发现失败**：多见于 `-cp` 热补丁启动（`META-INF/extensions` 没进 classpath）。请用 `-jar` 启动，并看启动日志里的「预热未发现任何分组定义」。
11. **本地模型目录缺失时能力不可用**：ONNX 供应商按 `<模型目录>/<modelId>/model.onnx` 读取，缺文件只报「未配置」，不会回退到云端。
12. **技能与角色是资源文件**：内置技能在 `src/main/resources/skills-builtin/*`、角色在 `characters-builtin/*`，新增后要重新打包（或走对应 SPI 的在线/离线来源）。
13. **示例配置不能当 `application.yml`**：`ai-starter.yml` 只由示例应用 `spring.config.import` 引入，改名会整份顶掉宿主配置。

## 八、附录：Actuator 只读端点

宿主 classpath 有 Actuator 时自动装配（依赖为 optional），用于回答「现在到底用的是哪个模型、配置从哪来、客户端有没有重建」。

| 端点 | 路径 | 内容 |
|---|---|---|
| `ai-config` | `/actuator/ai-config` | 24 个分组的生效值：`value / defaultValue / source(YAML\|DB\|DEFAULT) / masked / adminOnly`；凭据字段统一 `***` |
| `ai-models` | `/actuator/ai-models` | 各能力最终生效模型（页面 > `plugin.ai.default-model.*`），只列已配置项 |
| `ai-beans` | `/actuator/ai-beans` | 对话（默认客户端/聚合/provider/model 列表）、Agent 引擎解析与降级、人脸 / 重识别 / RAG 摘要、OCR 模型选择 |
| `ai-cache` | `/actuator/ai-cache` | `aiCacheManager` 各缓存的条目数、命中/未命中、淘汰数、命中率；Redis 管理器只列 Bean 名 |

```bash
curl -s localhost:8787/actuator/ai-models
# {"chat":"gpt-4.1-mini","configuredCount":4,"models":[...]}

curl -s localhost:8787/actuator/ai-config
# {"groupCount":24,"groups":{"ocr":{"groupName":"OCR 文字识别",
#   "fields":{"detector_model":{"value":"onnx:paddleocrv6-det","source":"DB",...},
#             "provider_api_key":{"value":"***","masked":true,...}}}}}
```

暴露配置：

```yaml
chua:
  ai:
    actuator:
      enabled: true            # 总开关；false 时不注册任何 AI 端点
management:
  endpoints:
    web:
      exposure:
        include: health,info,ai-config,ai-models,ai-beans,ai-cache
  endpoint:
    ai-config:
      enabled: true            # 单个端点也可单独关
```

> 端点只读、不改配置；`AiConfigEndpoint` 只读全局层与 YAML 覆盖，不依赖登录上下文，可在运维排障时直接 curl。
