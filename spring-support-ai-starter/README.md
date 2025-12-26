# Spring Support AI Starter

AI深度学习功能模块，提供企业级应用中常用的AI能力。

## 功能特性

- **人脸检测** - 检测图片中的人脸位置
- **人脸特征值** - 提取人脸特征向量，支持人脸比对
- **图片检测** - 检测图片中的物体
- **图片特征值** - 提取图片特征向量
- **文本特征值** - 提取文本特征向量
- **OCR识别** - 光学字符识别
- **版面分析** - 文档版面结构分析
- **性别年龄** - 人脸性别年龄检测

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

### 6. 直接使用AiService

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

如需自定义AI服务实现，可实现`AiService`接口并注册为Spring Bean：

```java
@Bean
public AiService customAiService() {
    return new CustomAiService();
}
```

## 依赖

- utils-support-deeplearning-starter
- utils-support-common-starter
