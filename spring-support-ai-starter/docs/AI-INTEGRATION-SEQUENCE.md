# AI 能力接入时序

本文说明「一个业务模块要具备 AI 能力」需要经过哪些环节、每个环节发生在什么时刻、失败时的表现是什么。
面向两类读者：接入 AI 的业务模块开发者、部署运维。

---

## 一、总体时序

```
① 构建期          业务模块 pom 依赖 spring-support-ai-starter
                        │
② 启动期（自动装配）  AutoConfiguration.imports → 5 个自动配置
                        │  AiConfiguration 再 @ComponentScan 全包
                        ▼
                   Bean 就绪：*Manager（实现 utils-xx 接口）+ controller 包下的 HTTP 接口
                        │
③ 启动期（配置加载）  GlobalSettingReader @PostConstruct 装载 YAML 掩盖层
                        │  AiConfiguration.onApplicationReady（HIGHEST_PRECEDENCE）
                        │    → 登记容器里的 ChatClient，设默认
                        │  AiConfiguration.refreshConfiguredChatClients（LOWEST_PRECEDENCE）
                        ▼    → 按「默认模型」配置重建客户端（UI 改完重启也生效）
                   AI 就绪
                        │
④ 运行期（按需）      业务调用 utils-xx 接口
                        │  首次访问时懒加载模型 / 按配置签名重建客户端
                        ▼
                   AI 能力可用
```

---

## 二、① 构建期：依赖

业务模块只需声明依赖：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-ai-starter</artifactId>
</dependency>
```

**业务代码不 import 该 jar 里的任何类**。所有注入点的类型来自 utils-xx：

| 契约（utils-xx） | 用途 | 实现（ai-starter） |
|---|---|---|
| `com.chua.common.support.ai.runtime.AiRuntimeConfig` | 生效模型、可用客户端、静态配置状态 | `DefaultAiRuntimeConfig` |
| `com.chua.deeplearning.support.paper.PaperClient` | 试卷识别、版面分组框 | `PaperClientManager` |
| `com.chua.common.support.ai.rag.RagClientProvider` | 按上下文取 RAG 客户端 | `RagClientManager` |
| `com.chua.common.support.ai.chat.ChatClient` | 对话调用 | 多种实现 + 聚合客户端 |

> eduai 模块是这套结构的参考实现：对 `com.chua.starter.ai.*` 的 import 数为 **0**。

---

## 三、② 启动期：自动装配

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 登记了 5 个自动配置：

```
com.chua.starter.ai.support.configuration.core.AiConfiguration          ← 总入口
com.chua.starter.ai.support.configuration.generate.AiGenerateAutoConfiguration
com.chua.starter.ai.support.configuration.protocol.AiProtocolServerConfiguration
com.chua.starter.ai.support.configuration.usage.UsageAutoConfiguration
com.chua.starter.ai.support.configuration.cache.AiCacheAutoConfiguration
```

`AiConfiguration` 额外标注 `@ComponentScan("com.chua.starter.ai.support")`，因此其下所有
`@Component`/`@Configuration`（管理器、控制器、适配器）自动注册，**无需业务方任何配置**。

**这一层不需要写代码**。只要 jar 在 classpath，Bean 与 HTTP 接口即就绪。

---

## 四、③ 启动期：配置加载与客户端重建

### 4.1 配置的三层来源

`GlobalSettingReader` 统一读取，优先级从高到低：

| 层级 | 存储位置 | 典型用途 | 生效时机 |
|---|---|---|---|
| YAML 掩盖层 | `plugin.ai.settings.<group>.<field>` | 运维固化模型参数，不可被 UI 覆盖 | 启动时 `@PostConstruct` 装载 |
| 用户级数据库 | `sys_ai_user_setting` | 单个用户的个性化设置 | 保存即生效 |
| 全局数据库 | `sys_ai_global_setting` | AI 控制台页面保存的默认值 | 保存即生效 |

启动日志可直接确认 YAML 掩盖层是否生效：

```
[GlobalSettingReader] YAML 掩盖层已加载，所有键优先于用户设置与数据库: ...
[GlobalSettingReader] YAML 掩盖层未启用（plugin.ai.settings.*），取值顺序为 用户设置 > 数据库 > 代码默认
```

> `runtime` 分组（provider / api_key / base_url / model）走 `getStrDirect` **直读数据库**，
> 不经缓存 —— 脚本直接改库也能立即生效，不必等页面保存去刷新缓存。

### 4.2 默认客户端重建（两个监听器，按顺序执行）

这是最容易踩坑的环节，历史缺陷是「UI 配了默认模型，重启后失效」：

| 监听器 | 顺序 | 职责 |
|---|---|---|
| `AiConfiguration#onApplicationReady` | `HIGHEST_PRECEDENCE` | 收集容器里的 `ChatClient`，登记进注册表，设默认，初始化主 Agent |
| `AiConfiguration#refreshConfiguredChatClients` | `LOWEST_PRECEDENCE` | 按「默认模型」配置重建真实客户端并设为默认 |

**为什么需要第二个**：`ChatClientHotUpdater` 只在「默认模型」设置变更事件里重建客户端；
若启动链路不补做，UI 配置只在「保存那一刻」有效，重启就退回启动时登记的聚合客户端，
表现为「配置没丢、但就是不生效」且无报错。

> 该逻辑原由 eduai 的 `EduAiChatClientBootstrap` 自行补做，现已收回 ai-starter 内部，
> 业务模块无需再写启动补丁。

---

## 五、④ 运行期：模型资产与懒加载

### 5.1 模型资产目录约定

本地推理（OCR / 版面 / 图像识别）需要 ONNX 模型文件，目录结构固定：

```
models/onnx/<modelId>/model.onnx
```

- 默认根目录：`models/onnx`（可由 `onnx.model_root` / `onnx.external_directories` 覆盖）
- 模型在**首次调用对应能力时**才加载，不阻塞启动

### 5.2 可用性判断

各管理器都提供 `available()` 语义（`PaperClient#available()` 等）；配置或资产缺失时返回
`false`/`null`，**不抛异常**。业务应以「是否为空」作为降级判据，而不是捕获异常。

> **`available()` 判的是「管线能否真正装配」，不是「配置里有没有填模型名」。**
> 二者不是一回事：模型名填了、但模型文件缺失时，装配会失败。若以「填了名字」为准则会放行，
> 调用方随后在识别阶段撞上异常。因此实现内部以管线装配结果为准，并在装配失败后按配置指纹
> 记忆该结果，避免每次调用重复尝试加载模型。

典型降级链（以 eduai 阅卷为例）：

```
PaperClient.available() == false
  → 抛出可操作异常：「试卷识别管线不可用：请检查 ocr/paper 分组与模型资产是否就绪」
  → 无版面模型时 PaperPipeline 退化为整图 OCR（版面是增强能力，缺失不阻断）
```

---

## 六、前端

`pages/ai` 是 `vue-support-parent-starter` 的 **turbo workspace 独立模块**：

```yaml
# pnpm-workspace.yaml
packages:
  - "packages/*"
  - "pages/*"
```

```json
// turbo.json
"build": { "dependsOn": ["^build"], "outputs": ["dist/**"] }
```

要点：

1. **后端 jar 不携带前端产物** —— `spring-support-ai-starter` 无 `static` 资源、无 npm/vite 构建插件
2. 前端按 turbo 任务独立构建：`pnpm build --filter=pages/ai`
3. 构建产物由前端服务器（nginx 等）托管，与后端 jar 的发布相互独立
4. 前端通过后端 `com.chua.starter.ai.support.controller` 包暴露的 `/ai/**` 等接口通信

---

## 七、接入检查清单

按顺序核对，每一条可独立定位问题：

| # | 检查项 | 如何确认 | 失败表现 |
|---|---|---|---|
| 1 | pom 已依赖 ai-starter | `mvn dependency:tree` | 注入点为 null |
| 2 | 数据库可用、flyway 已建表 | `sys_ai_global_setting` 表存在 | 启动失败或配置页空白 |
| 3 | 配置已就位（YAML 或页面） | 启动日志看 YAML 掩盖层；或查表 | 能力探测 `available=false` |
| 4 | provider 凭据已配 | 页面保存后有 `[AiConfiguration] 已按...重建默认对话客户端` 日志 | 「启用了却没密钥」告警 |
| 5 | 模型资产已就位 | `models/onnx/<modelId>/model.onnx` 存在 | 识别报「管线不可用」 |
| 6 | 前端已独立部署 | `pages/ai` 构建产物已托管 | 无界面可操作 |

---

## 八、常见误判

| 误判 | 实际情况 |
|---|---|
| 「引入依赖就有全部能力」 | 后端 Bean + HTTP 接口就绪；模型资产、凭据、前端产物仍需各自就位 |
| 「配了 YAML 就不用数据库」 | 数据库仍需可用（flyway 建表、页面配置依赖表结构）；YAML 只是优先级最高的那一层 |
| 「重启后配置失效」 | 已由 `refreshConfiguredChatClients`（`LOWEST_PRECEDENCE`）修复 |
| 「改了库要重启才生效」 | `runtime` 分组走 `getStrDirect` 直读库，改库即生效 |
| 「业务代码得 import ai-starter」 | 不需要；注入点类型全部来自 utils-xx |
| 「配置里填了模型名就算可用」 | `available()` 以管线能否真正装配为准；模型文件缺失时为 false |

---

## 九、参考实现

`spring-support-eduai-starter` 是完整样例：

```
PaperClient           → EduPaperScanner（阅卷识别：文字块 + 版面框）
RagClientProvider     → EduKnowledgeDocServiceImpl（知识库向量化）
AiRuntimeConfig       → EduAiService（组卷 / 判分 / 能力探测）
ChatClient            → EduAiService 内部按配置构建并调用（ChatClient.create）
```

三个契约的注入点均为 `ObjectProvider<X>` 或 `@Autowired(required = false)`，实现缺失时自动降级。

---

*文档随 `spring-support-ai-starter` 与 `utils-support-*` 的契约变化同步维护。*
