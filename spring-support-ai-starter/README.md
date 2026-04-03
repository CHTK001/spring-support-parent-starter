# Spring Support AI Starter

AI深度学习功能模块，提供企业级应用中常用的AI能力。

## 功能特性

### 图像处理能力
- **人脸检测** - 检测图片中的人脸位置
- **人脸特征值** - 提取人脸特征向量，支持人脸比对
- **图片检测** - 检测图片中的物体
- **图片特征值** - 提取图片特征向量
- **OCR识别** - 光学字符识别
- **版面分析** - 文档版面结构分析
- **性别年龄** - 人脸性别年龄检测

### 设计特点
- **链式API** - 提供流畅的调用体验
- **多模式支持** - 同步、异步、响应式三种调用模式
- **类型安全** - 强类型的结果对象
- **易于扩展** - 基于SPI机制，支持自定义实现
- **MCP支持** - 支持MCP（Model Context Protocol）前后置处理器，可扩展LLM对话能力

## 注意事项

⚠️ **重要说明**：
- `AiChat`类主要用于**图像处理**功能，非LLM对话功能
- 如需LLM对话能力，建议使用专门的ChatClient实现
- `ChatClient`支持MCP（Model Context Protocol）前后置处理器，可通过SPI机制扩展
- `AiClient`提供身份识别功能的简化操作接口，封装了`IdentificationEngine`

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-ai-starter</artifactId>
    <version>${version}</version>
</dependency>
```

### 2. 配置属性

```yaml
spring:
  ai:
    enabled: true
    default-provider: openai
    providers:
      openai:
        api-key: your-api-key
        base-url: https://api.openai.com/v1
    llm:
      factory: default             # common ChatClient.Factory SPI 名称
      provider: openai
      model: gpt-4o-mini
      system: 你是一个代码重构助手
      input-optimization-enabled: true
      context-compression-enabled: true
      mcp:
        enabled: true
    agent:
      type: default
      provider: openai
      model: gpt-4o-mini
      snapshot-enabled: true
      execution-record-enabled: true
    model-path: /path/to/models    # 本地模型目录
    face-detection:
      enabled: true                # 是否启用人脸检测
      confidence-threshold: 0.5    # 置信度阈值
      nms-threshold: 0.4           # 非极大值抑制阈值
    ocr:
      enabled: true                # 是否启用 OCR
      language: chi_sim            # OCR 语言包
    image:
      enabled: true                # 是否启用图像特征提取
      feature-dimension: 512       # 图像特征维度
    text:
      enabled: true                # 是否启用文本能力
      max-sequence-length: 512     # 文本最大序列长度
```

### 3. 使用链式API（同步模式）

```java
@Autowired
private AiService aiService;

// 或使用工厂模式
@Autowired
private AiChatFactory aiChatFactory;

// 人脸检测
FaceDetectionResult result = AiChat.of(aiService)
    .image(imageBytes)
    .detectFaces();

// 人脸特征提取
FeatureResult feature = AiChat.of(aiService)
    .image(imageFile)
    .faceFeature();

// 图片物体检测
ImageDetectionResult objects = AiChat.of(aiService)
    .image(imageBytes)
    .detectImage();

// OCR识别
OcrResult ocrResult = AiChat.of(aiService)
    .image(imageFile)
    .ocr();

// 文本特征
FeatureResult textFeature = AiChat.of(aiService)
    .text("Hello World")
    .textFeature();

// 版面分析
LayoutAnalysisResult layout = AiChat.of(aiService)
    .image(imageBytes)
    .analyzeLayout();

// 性别年龄检测
GenderAgeResult genderAge = AiChat.of(aiService)
    .image(imageFile)
    .detectGenderAge();
```

### 4. 异步模式 (CompletableFuture)

```java
// 异步人脸检测
CompletableFuture<FaceDetectionResult> future = AiChat.of(aiService)
    .image(imageBytes)
    .detectFacesAsync();

// 异步OCR识别
CompletableFuture<OcrResult> ocrFuture = AiChat.of(aiService)
    .image(imageFile)
    .ocrAsync();

// 等待结果
FaceDetectionResult result = future.get();

// 链式异步处理
AiChat.of(aiService)
    .image(imageBytes)
    .detectFacesAsync()
    .thenAccept(r -> System.out.println("检测到 " + r.getFaces().size() + " 个人脸"));
```

### 5. Reactor响应式模式 (Mono)

```java
// 启用Reactor支持后使用
Mono<FaceDetectionResult> mono = AiChat.of(aiService)
    .withReactor()
    .image(imageBytes)
    .detectFacesMono();

// 订阅并处理结果
mono.subscribe(result -> {
    System.out.println("检测到 " + result.getFaces().size() + " 个人脸");
});

// 链式响应式处理
AiChat.of(aiService)
    .withReactor()
    .image(imageFile)
    .ocrMono()
    .map(OcrResult::getFullText)
    .subscribe(text -> System.out.println("识别结果: " + text));
```

### 6. 使用AiClient（身份识别简化接口）

```java
@Autowired
private IdentificationEngine identificationEngine;

// 创建AiClient
AiClient aiClient = AiClient.builder()
    .engine(identificationEngine)
    .build();

// 人脸检测
FaceDetectionResult faces = aiClient.detectFaces(imageBytes);

// 提取人脸特征
FeatureResult feature = aiClient.extractFaceFeature(imageFile);

// 人脸比对
float similarity = aiClient.compareFaces(image1, image2);
```

### 7. 使用ChatClient（scope + MCP）

```java
@Autowired
private ChatClient chatClient;

ChatContext context = new ChatContext();
context.setHistory(List.of(
    ChatMessage.user("请记住我在看 Spring AI 模块"),
    ChatMessage.assistant("好的，我会基于这个上下文继续回答")
));

ChatResponse response = chatClient.chat(ChatScope.builder()
    .input("帮我梳理 chatclient 和 agent 的边界")
    .model("gpt-4.1-mini")
    .context(context)
    .inputOptimizationEnabled(true)
    .contextCompressionEnabled(true)
    .build());

System.out.println(response.getText());
```

### 8. 使用Agent（session + snapshot）

```java
@Autowired
private Agent agent;

AgentResponse first = agent.execute(AgentRequest.builder()
    .sessionId("editor-1")
    .input("先创建一个重构清单")
    .build());

agent.session("editor-1").useModel("gpt-4.1");

AgentResponse second = agent.execute(AgentRequest.builder()
    .sessionId("editor-1")
    .input("继续完善刚才的方案")
    .build());

List<AgentSnapshot> snapshots = agent.snapshots("editor-1");
```

### 9. 直接使用AiService

```java
@Autowired
private AiService aiService;

@Autowired
private AsyncAiService asyncAiService;

@Autowired(required = false)
private ReactiveAiService reactiveAiService;

// 同步人脸检测
FaceDetectionResult faces = aiService.detectFaces(imageBytes);

// 异步OCR识别
CompletableFuture<OcrResult> ocrFuture = asyncAiService.ocrAsync(new File("image.png"));

// 响应式特征提取
Mono<FeatureResult> featureMono = reactiveAiService.extractFaceFeatureMono(imageBytes);

// 特征比对
FeatureResult feature1 = aiService.extractFaceFeature(image1);
FeatureResult feature2 = aiService.extractFaceFeature(image2);
float similarity = feature1.cosineSimilarity(feature2);
```

## API说明

### AiService接口

| 方法 | 说明 |
|------|------|
| `detectFaces(byte[]/File/InputStream)` | 人脸检测 |
| `extractFaceFeature(byte[]/File)` | 人脸特征提取 |
| `detectGenderAge(byte[]/File)` | 性别年龄检测 |
| `detectImage(byte[]/File)` | 图片物体检测 |
| `extractImageFeature(byte[]/File)` | 图片特征提取 |
| `extractTextFeature(String)` | 文本特征提取 |
| `ocr(byte[]/File/InputStream)` | OCR识别 |
| `analyzeLayout(byte[]/File)` | 版面分析 |

### AiClient接口（身份识别简化接口）

| 方法 | 说明 |
|------|------|
| `detectFaces(byte[]/File)` | 人脸检测 |
| `extractFaceFeature(byte[]/File)` | 人脸特征提取 |
| `compareFaces(byte[]/File, byte[]/File)` | 人脸比对，返回相似度（0-1） |

### ChatClient接口（支持MCP）

| 方法 | 说明 |
|------|------|
| `chat(ChatScope scope)` | 执行一次 scope 聊天 |
| `chat(ChatScope scope, Consumer<String>, Runnable, Consumer<Throwable>)` | 执行一次流式 scope 聊天 |
| `listModels()` | 返回当前 provider 支持的模型目录 |
| `listTools(ChatScope scope)` | 返回当前 scope 可用工具 |
| `callTool(ChatScope scope, AiToolCall)` | 直接调用 MCP 工具 |
| `chatSync(String input)` | 使用最简单的默认 scope 调用 |

### AiChat链式API

| 方法 | 说明 |
|------|------|
| `image(byte[]/File/InputStream)` | 设置图片输入 |
| `text(String)` | 设置文本输入 |
| `withReactor()` | 启用Reactor响应式支持 |
| `detectFaces()` | 同步人脸检测 |
| `detectFacesAsync()` | 异步人脸检测 |
| `detectFacesMono()` | 响应式人脸检测 |
| `faceFeature()` / `faceFeatureAsync()` / `faceFeatureMono()` | 提取人脸特征 |
| `detectGenderAge()` / `detectGenderAgeAsync()` / `detectGenderAgeMono()` | 检测性别年龄 |
| `detectImage()` / `detectImageAsync()` / `detectImageMono()` | 图片检测 |
| `imageFeature()` / `imageFeatureAsync()` / `imageFeatureMono()` | 提取图片特征 |
| `textFeature()` / `textFeatureAsync()` / `textFeatureMono()` | 提取文本特征 |
| `ocr()` / `ocrAsync()` / `ocrMono()` | OCR识别 |
| `analyzeLayout()` / `analyzeLayoutAsync()` / `analyzeLayoutMono()` | 版面分析 |

## 扩展

### 自定义AI服务实现

如需自定义AI服务实现，可实现`AiService`接口并注册为Spring Bean：

```java
@Bean
public AiService customAiService() {
    return new CustomAiService();
}
```

### SPI扩展机制

框架基于SPI机制支持多种AI服务提供商，可通过配置文件指定：

```yaml
spring:
  ai:
    face-detection:
      provider: baidu  # 支持 default, baidu, megvii, sensetime
    ocr:
      provider: paddleocr  # 支持 default, baidu, tesseract, paddleocr
    image:
      provider: aliyun  # 支持 default, baidu, aliyun
    text:
      provider: openai  # 支持 default, openai, baidu
```

## MCP支持说明

### 架构设计

- **BigModelClient = provider + model catalog + inference**
- **ChatClient = BigModelClient + MCP + scope**
- **Agent = ChatClient + session + snapshot + permission**
- **MCP统一抽象**：当前 MCP 直接复用 `common` 模块的 `McpProvider / McpClient / McpPlugin`
- **自动工具编排**：默认 `ChatClient` 实现会自动处理 tool call / MCP loop

### ChatClient MCP支持

- ✅ **scope内启用MCP**：每次调用可独立控制是否启用 MCP
- ✅ **自动tool loop**：模型返回 tool call 后自动继续编排
- ✅ **输入美化**：可用当前模型优化输入
- ✅ **上下文压缩**：可用当前模型压缩历史上下文
- ✅ **模型热切换**：在当前 provider 支持范围内按 scope 切换 model
- ✅ **SPI复用**：底层实现通过 common `ChatClient.Factory` SPI 接入，默认实现与后续 `langchain4j` 实现可复用同一 Spring `ChatClient/Agent` 外层

### 使用建议

1. `ChatClient` 只负责单次 scope，不要把 session 放进去
2. `Agent` 负责会话、快照、权限和任务编排
3. provider 固定在 Agent 生命周期内，model 在 provider 内热切换
4. `AgentRequest.model > AgentSession.model > Agent.defaultModel`
5. **上下文管理**：合理使用上下文传递必要信息

## 依赖

- utils-support-deeplearning-starter - 深度学习工具库
- utils-support-common-starter - 通用工具类库
- reactor-core (可选) - 响应式编程支持
