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
    model-path: /path/to/models
    face-detection:
      enabled: true
      confidence-threshold: 0.5
      nms-threshold: 0.4
    ocr:
      enabled: true
      language: chi_sim
    image:
      enabled: true
      feature-dimension: 512
    text:
      enabled: true
      max-sequence-length: 512
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

### 7. 使用ChatClient（支持MCP前后置处理）

```java
// 创建基础ChatClient
ChatClient baseClient = ChatClient.create("openai", "your-api-key");

// 使用Builder构建带MCP处理的ChatClient
ChatClient chatClient = ChatClient.builder()
    .client(baseClient)
    .withPreprocessors()  // 自动加载MCP前置处理器
    .withPostprocessors() // 自动加载MCP后置处理器
    .build();

// 使用ChatClient（会自动执行MCP前后置处理）
String response = chatClient.chat("你好", new ChatContext());
```

### 8. 直接使用AiService

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
| `chat(String message)` | 发送聊天消息 |
| `chat(String message, ChatContext context)` | 发送聊天消息（带上下文） |
| `Builder.client(ChatClient)` | 设置基础ChatClient实现 |
| `Builder.withPreprocessors()` | 自动加载MCP前置处理器 |
| `Builder.withPostprocessors()` | 自动加载MCP后置处理器 |
| `Builder.build()` | 构建带MCP处理的ChatClient |

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

### MCP前后置处理器扩展

框架支持通过SPI机制扩展MCP前后置处理器：

```java
// 实现MCP前置处理器
@Component
public class CustomMcpPreprocessor implements McpPreprocessor {
    
    @Override
    public int getPriority() {
        return 100; // 优先级，数字越小优先级越高
    }
    
    @Override
    public String preprocess(String rawInput, ChatContext context) {
        // 自定义预处理逻辑
        return rawInput.trim();
    }
}

// 实现MCP后置处理器
@Component
public class CustomMcpPostprocessor implements McpPostprocessor {
    
    @Override
    public int getPriority() {
        return 100;
    }
    
    @Override
    public String postprocess(String rawOutput, ChatContext context) {
        // 自定义后处理逻辑
        return rawOutput;
    }
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

- **底层接口**：`utils-support-deeplearning-starter` 提供通用的MCP接口（`com.chua.deeplearning.support.ml.bigmodel.mcp.McpPreprocessor`、`McpPostprocessor`）
- **Spring层适配**：`spring-support-ai-starter` 提供Spring层的适配接口，通过`ChatContextAdapter`进行上下文转换
- **自动加载**：通过SPI机制自动发现和加载MCP处理器，支持优先级排序

### ChatClient MCP支持

- ✅ **支持前置处理器**：在LLM处理前对输入进行预处理
- ✅ **支持后置处理器**：在LLM处理后对输出进行后处理
- ✅ **优先级排序**：处理器按优先级顺序执行
- ✅ **异常处理**：单个处理器异常不影响整体流程
- ✅ **上下文传递**：支持上下文信息在处理器间传递

### 使用建议

1. **合理使用优先级**：根据业务需求设置处理器优先级
2. **异常处理**：确保处理器内部异常不会影响主流程
3. **性能考虑**：避免在处理器中执行耗时操作
4. **上下文管理**：合理使用上下文传递必要信息

## 依赖

- utils-support-deeplearning-starter - 深度学习工具库
- utils-support-common-starter - 通用工具类库
- reactor-core (可选) - 响应式编程支持
