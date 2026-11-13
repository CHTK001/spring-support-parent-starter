# spring-support-ueba-starter

把 `utils-support-ueba-starter` 的 UEBA 分析引擎装配成可直接注入的 Spring Bean。
纯自动配置模块：不做 REST、不做持久化，只负责「按配置把 `UebaEngine` 建好，并在容器销毁时释放模型资源」。

## 一、模块能力

| 能力域 | 关键类 | 入口 |
| --- | --- | --- |
| 分析引擎自动装配 | `UebaAutoConfiguration` | `UebaAutoConfiguration#uebaEngine(UebaProperties)` |
| 配置属性绑定 | `UebaProperties` | `@ConfigurationProperties(prefix = "plugin.ueba")` |
| 单条流量事件综合分析（IP 异常 + 行为序列 + 风险融合 + 文字解释） | `UebaEngine` | `UebaEngine#analyze(TrafficEvent)` |
| 链式构建引擎（门面） | `Ueba` / `UebaEngineBuilder` | `Ueba.engine()...build()` |
| 配置文件来源选择（外部文件优先） | `UebaAutoConfiguration` | `configPath` > `configResource` |
| 模型目录注入 | `UebaEngineBuilder#modelDir(String)` | 写入系统属性 `ueba.model.dir` |
| LLM 语义解释开关 | `UebaEngineBuilder#enableLlm()` / `#disableLlm()` | `plugin.ueba.llm` |
| 引擎资源释放 | `UebaEngine#close()` | `@Bean(destroyMethod = "close")` |
| 服务信息登记 | `UebaAutoConfiguration#uebaServiceInfoContributor` | `ServiceInfoContributor` |

自动配置入口：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` →
`com.chua.starter.ueba.configuration.UebaAutoConfiguration`。

装配条件（两个同时满足）：

1. `@ConditionalOnClass(UebaEngine.class)` —— 运行期必须有 `utils-support-ueba-starter`；
2. `@ConditionalOnProperty(prefix = "plugin.ueba", name = "enabled", havingValue = "true", matchIfMissing = true)`。

`uebaEngine` 带 `@ConditionalOnMissingBean`：宿主自己声明 `UebaEngine` 时本模块自动退避。

## 二、依赖与引入

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-ueba-starter</artifactId>
    <version>4.0.0.51</version>
</dependency>
```

| 依赖 | 版本 | 作用 |
| --- | --- | --- |
| `com.chua:utils-support-ueba-starter` | `${utils.version}` = `4.0.0.50` | `UebaEngine` / `UebaEngineBuilder` / `UebaConfig` / `TrafficEvent` / `UebaResult` 等引擎实现 |
| `org.springframework.boot:spring-boot-autoconfigure` | 由父 POM 管理 | `@AutoConfiguration` / 条件装配 |
| `org.springframework.boot:spring-boot-configuration-processor` | 由父 POM 管理（`optional`） | 生成 `plugin.ueba.*` 配置元数据 |
| `com.chua:spring-support-common-starter` | `4.0.0.51`（`optional`） | `ServiceInfoContributor`（缺失时不登记服务信息，不影响引擎装配） |
| `org.projectlombok:lombok` | 由父 POM 管理（`optional`） | `@Data` / `@Slf4j` |

父 POM：`com.chua:spring-support-parent-starter:4.0.0.51`，`java.version = 25`。

> 注意：本模块 POM 把 `spring-boot-maven-plugin` 显式 `skip=true`，产物是普通 jar，不是可执行 fat jar。

## 三、配置项

前缀 `plugin.ueba`，绑定类 `com.chua.starter.ueba.configuration.UebaProperties`。

| 配置 key | 默认值 | 说明 |
| --- | --- | --- |
| `plugin.ueba.enabled` | `true` | 是否装配 `UebaEngine`；`false` 时整个自动配置不生效 |
| `plugin.ueba.llm` | `true` | 是否启用 minimind 语义解释；`false` 时使用模板解释文案 |
| `plugin.ueba.config-resource` | `ueba-config.yaml` | classpath 上的配置文件路径（`config-path` 为空时使用） |
| `plugin.ueba.config-path` | `""` | 外部配置文件路径，优先级高于 `config-resource` |
| `plugin.ueba.model-dir` | `""` | 模型目录，非空时写入系统属性 `ueba.model.dir`；autoEncoder 与 LSTM 模型都从该目录加载 |

```yaml
plugin:
  ueba:
    enabled: true
    llm: true
    config-resource: ueba-config.yaml
    # config-path: D:/config/ueba-config.yaml
    # model-dir: D:/models/ueba
```

配置来源优先级（`UebaAutoConfiguration#uebaEngine` 内的分支）：

1. `plugin.ueba.config-path` 非空 → `builder.configFile(...)`；
2. 否则 → `builder.configResource(plugin.ueba.config-resource)`，默认取
   `utils-support-ueba-starter` 自带的那份 `ueba-config.yaml`。

引擎配置文件本身不是 Spring 配置项，它的段结构为 `features` / `autoEncoder` / `lstm` / `risk` / `preprocessing`；
其中 `features` 的顺序就是 AutoEncoder 输入向量的维度顺序，训练端与推理端必须一致。
自带默认值示例：`autoEncoder.threshold=0.8`、`lstm.windowSize=20`、`lstm.numClasses=3`、
`lstm.classLabels=[normal, suspicious, attack]`、`risk.ipWeight=0.5`、`risk.behaviorWeight=0.5`、
`risk.highThreshold=0.7`、`risk.mediumThreshold=0.35`。

## 四、快速开始

**第一步：引入依赖**（见第二节）。宿主需要保证 `utils-support-ueba-starter` 在运行期 classpath 上
（由本模块的 compile 依赖传递带入，除非被宿主排除）。

**第二步：准备配置文件与模型**。不改配置时直接使用 classpath 上的 `ueba-config.yaml`；
要用自己的模型，把 ONNX 文件放进某个目录并设置 `plugin.ueba.model-dir`。

**第三步：注入 `UebaEngine` 调 `analyze`**（见下一节）。模块不提供 REST 端点，入口就是 Bean。

## 五、运行示例

### 5.1 调用示例

```java
@Service
public class AccessLogUebaService {

    @Resource                       // 由 UebaAutoConfiguration 注册，容器关闭时自动 close()
    private UebaEngine uebaEngine;

    public void onAccessLog(String ip, String path, int status, long timestamp) {
        TrafficEvent event = TrafficEvent.builder()
                .ip(ip)
                .path(path)
                .method("GET")
                .statusCode(status)
                .userAgent("curl/8.5.0")
                .timestamp(timestamp)
                .responseTimeMs(35)
                .responseSizeBytes(1024L)
                .sessionId("sess-0001")
                .build();

        UebaResult result = uebaEngine.analyze(event);   // 同步调用，内部有滑动窗口与模型推理

        String entity = result.getEntityId();                     // IP 优先，缺失时用 sessionId，都没有则 "unknown"
        double risk = result.getRiskScore();                      // [0,1]
        UebaResult.RiskLevel level = result.getRiskLevel();       // NORMAL / LOW / MEDIUM / HIGH（见第七节第 6 条）
        String explanation = result.getExplanation();             // MiniMind 生成，或模板文案
        String ipLevel = result.getIpAnomaly().getLevel().name();  // NORMAL / LOW / MEDIUM / HIGH / CRITICAL
        String behaviorLabel = result.getBehavior().getLabel();    // normal / suspicious / attack
        double confidence = result.getBehavior().getConfidence();
    }
}
```

`TrafficEvent` 的字段会被 `FeatureExtractor` 按 `ueba-config.yaml` 的 `features` 定义消费
（请求速率、路径熵、路径数、错误率、方法多样性、UA 多样性、响应耗时、响应体大小、时段等）；
`sessionId` 只在 `ip` 为空时用作实体键。具体取用规则以 `FeatureExtractor` 代码为准。

风险融合规则（`UebaEngine#computeRisk`）：

```text
ipScore   = clamp(reconstructionError / threshold, 0, 1)   // threshold<=0 时按 0 处理
riskScore = clamp(risk.ipWeight * ipScore + risk.behaviorWeight * behavior.riskScore, 0, 1)
```

等级判定（`UebaEngine#classifyRisk`）：`>= highThreshold` → `HIGH`；`>= mediumThreshold` → `MEDIUM`；
`> 0.15` → `LOW`；其余 `NORMAL`。

**链式 API 在引擎构建器上，不在本模块里**。本模块没有自己的链式 API——它只是把下面这条链
在自动配置里替你调好；需要自定义时直接自己建：

```java
UebaEngine engine = Ueba.engine()
        .configResource("ueba-config.yaml")   // 或 .configFile("D:/config/ueba-config.yaml")
        .modelDir("D:/models/ueba")           // 写入系统属性 ueba.model.dir
        .enableLlm()                          // 或 .disableLlm()
        .build();
```

`UebaEngineBuilder` 的可用方法：`config(UebaConfig)`、`config(Path)`、`configFile(String)`、
`configResource(String)`、`modelDir(String)`、`enableLlm()`、`disableLlm()`、`build()`。
其中除 `build()` 外的每个 setter 都 `return this`。

不想走 Spring 时还有一个静态快捷入口 `UebaEngine#loadDefault()`（读 classpath 的 `ueba-config.yaml`）。

### 5.2 SPI 扩展示例

**本模块不提供 SPI 扩展点**。模块里没有任何 `META-INF/services` 文件，也不定义自己的扩展接口。

可用的扩展方式只有 Spring 层的 Bean 退避：自定义同类型 `UebaEngine` Bean 即可接管装配
（`@ConditionalOnMissingBean` 会让自动配置让位）：

```java
@Configuration
public class MyUebaConfig {

    @Bean(destroyMethod = "close")
    public UebaEngine uebaEngine() {
        return Ueba.engine()
                .configFile("D:/config/ueba-config.yaml")
                .modelDir("D:/models/ueba")
                .disableLlm()
                .build();
    }
}
```

## 六、与其它 starter 的关系

| 模块 | 方向 | 说明 |
| --- | --- | --- |
| `utils-support-ueba-starter` | 本模块依赖它 | 引擎本体：`UebaEngine`、`UebaEngineBuilder`、`UebaConfig`、`FeatureExtractor`、`MiniMindUebaAnalyzer`、`IpBehaviorTracker`、`RuleBasedScorer`，以及 classpath 配置 `ueba-config.yaml` |
| `spring-support-common-starter` | 本模块可选依赖它 | `ServiceInfoContributor`（`optional`，缺失时服务信息不登记） |
| `utils-support-deeplearning-*`（ONNX） | 经 `utils-support-ueba-starter` 传递 | `AutoEncoderIpTranslator` / `LstmAttentionBehaviorTranslator` 的模型加载与推理 |
| 其它 `spring-support-*` | 无直接关系 | 本模块不依赖 web / mybatis / socket，也不发布 HTTP 端点 |

当前父 POM 中没有任何模块声明依赖 `spring-support-ueba-starter`（父 POM 第 748 行附近仅为
`dependencyManagement` 条目，不是使用方）。下游集成以宿主业务代码注入 `UebaEngine` 为主。

## 七、已知限制

1. **没有 REST 端点、没有持久化、没有调度**。模块只装配一个引擎 Bean，
   采集、落库、告警、定时任务全部由宿主负责。
2. **没有 SPI**。想换分析算法只能替换整个 `UebaEngine` Bean（见 5.2），无法按接口挂多个实现。
3. **模型需自备**。`autoencoder_ip.onnx` / `lstm_attention_behavior.onnx` 缺失或推理失败时，
   `detectIp` / `analyzeBehavior` 会各自回退到 `RuleBasedScorer`，分析流程不中断，
   但此时 `BehaviorProfile#getAnalysisMode()` 不再为 `ML`。
4. **`modelDir` 是 JVM 全局副作用**。`UebaEngineBuilder#build()` 用 `System.setProperty("ueba.model.dir", ...)` 写入，
   同一 JVM 内创建多个引擎且目录不同时，后写的会覆盖先写的。
5. **窗口状态在内存**。`IpBehaviorTracker` 的滑动窗口随进程消失；分析依赖历史窗口，
   冷启动阶段样本不足：AutoEncoder 需累计至少 `AE_MIN_SAMPLES = 30` 个误差样本才开启阈值判定
   （warm-up 期间阈值为 `Double.MAX_VALUE`，即不告警）。
6. **`CRITICAL` 不会出现在综合分析等级里**。`UebaResult.RiskLevel` 定义了 `CRITICAL`，
   但 `classifyRisk` 只产出 `NORMAL` / `LOW` / `MEDIUM` / `HIGH`；`CRITICAL` 只可能出现在
   `IpAnomalyResult.AnomalyLevel`（按 `err / threshold >= 3.0` 映射）。
7. **LLM 解释是尽力而为**。`plugin.ueba.llm=true` 但 `MiniMindUebaAnalyzer` 不可用时，
   解释会退化为固定模板："实体 %s：IP 异常等级 %s，行为类别 %s，综合风险 %.2f。"
8. **配置文件缺段直接抛异常**。`UebaEngine` 构造时校验 `features` / `autoEncoder` / `lstm` / `risk`
   四段，缺失抛 `IllegalArgumentException`，应用启动会失败——这是有意的快速失败。
9. **`analyze` 是同步阻塞调用**。引擎内部对状态做了同步，可多线程并发调用，
   但每个事件都会走一遍特征提取 + 两次模型推理，宿主需要自行控制采样率与线程池大小。
