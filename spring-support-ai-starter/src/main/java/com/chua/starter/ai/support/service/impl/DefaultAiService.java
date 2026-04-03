package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.ai.AiClient;
import com.chua.common.support.ai.Detector;
import com.chua.common.support.ai.Feature;
import com.chua.common.support.ai.face.api.FaceDetector;
import com.chua.common.support.ai.face.api.FaceFeature;
import com.chua.common.support.ai.ocr.api.OcrRecognizer;
import com.chua.common.support.ai.ocr.model.OcrResult;
import com.chua.common.support.ai.result.DetectorPredictResult;
import com.chua.common.support.ai.result.HumanPredictResult;
import com.chua.common.support.ai.result.PredictResult;
import com.chua.common.support.ai.result.PredictResultObject;
import com.chua.common.support.core.spi.ServiceProvider;
import com.chua.starter.ai.support.model.*;
import com.chua.starter.ai.support.properties.AiProperties;
import com.chua.starter.ai.support.service.AiService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI服务默认实现
 * <p>
 * 基于utils-support-deeplearning-starter的AI服务实现
 *
 * @author CH
 * @since 2024-01-01
 */
@Slf4j
public class DefaultAiService implements AiService {

    private final AiProperties aiProperties;
    
    /**
     * AiClient实例（新架构）
     */
    @Getter
    private final AiClient aiClient;
    
    /**
     * SPI 加载的所有人脸检测器实现（按 provider 名称索引）
     */
    private final Map<String, FaceDetector> faceDetectorMap = new ConcurrentHashMap<>();
    
    /**
     * SPI 加载的所有人脸特征提取器实现（按 provider 名称索引）
     */
    private final Map<String, FaceFeature> faceFeatureMap = new ConcurrentHashMap<>();
    
    /**
     * SPI 加载的所有检测器实现（按名称索引，如 "image-aliyun", "layout-baidu"）
     */
    private final Map<String, Detector> detectorMap = new ConcurrentHashMap<>();
    
    /**
     * SPI 加载的所有特征提取器实现（按名称索引，如 "image-aliyun", "text-openai"）
     */
    private final Map<String, Feature> featureMap = new ConcurrentHashMap<>();
    
    /**
     * SPI 加载的所有 OCR 识别器实现（按 provider 名称索引）
     */
    private final Map<String, OcrRecognizer> ocrRecognizerMap = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param aiProperties AI配置属性
     * @param aiClient AiClient实例
     */
    public DefaultAiService(AiProperties aiProperties, AiClient aiClient) {
        this.aiProperties = aiProperties;
        this.aiClient = aiClient;
        
        // 使用 SPI 机制加载所有实现
        loadAllImplementations();
    }
    
    /**
     * 使用 SPI 机制加载所有 AI 能力实现
     */
    private void loadAllImplementations() {
        // 加载人脸检测器
        ServiceProvider.of(FaceDetector.class).getExtensions().forEach(name -> {
            FaceDetector detector = ServiceProvider.of(FaceDetector.class).getExtension(name);
            if (detector != null) {
                faceDetectorMap.put(extractProviderName(name), detector);
            }
        });
        
        // 加载人脸特征提取器
        ServiceProvider.of(FaceFeature.class).getExtensions().forEach(name -> {
            FaceFeature feature = ServiceProvider.of(FaceFeature.class).getExtension(name);
            if (feature != null) {
                faceFeatureMap.put(extractProviderName(name), feature);
            }
        });
        
        // 加载检测器
        ServiceProvider.of(Detector.class).getExtensions().forEach(name -> {
            Detector detector = ServiceProvider.of(Detector.class).getExtension(name);
            if (detector != null) {
                detectorMap.put(name, detector);
            }
        });
        
        // 加载特征提取器
        ServiceProvider.of(Feature.class).getExtensions().forEach(name -> {
            Feature feature = ServiceProvider.of(Feature.class).getExtension(name);
            if (feature != null) {
                featureMap.put(name, feature);
            }
        });
        
        // 加载 OCR 识别器
        ServiceProvider.of(OcrRecognizer.class).getExtensions().forEach(name -> {
            OcrRecognizer recognizer = ServiceProvider.of(OcrRecognizer.class).getExtension(name);
            if (recognizer != null) {
                ocrRecognizerMap.put(extractProviderName(name), recognizer);
            }
        });
    }
    
    /**
     * 从类名中提取 provider 名称
     * 例如：AliyunFaceDetector -> aliyun
     *
     * @param className 类名（小写）
     * @return provider 名称
     */
    private String extractProviderName(String className) {
        // 移除常见后缀
        String name = className
            .replace("facedetector", "")
            .replace("facefeature", "")
            .replace("detector", "")
            .replace("feature", "")
            .replace("recognizer", "")
            .replace("ocr", "");
        return name;
    }

    // ==================== 获取检测器实例 ====================

    /**
     * 获取人脸检测器实例（从 SPI 加载的 Map 中获取）
     *
     * @return 人脸检测器
     */
    private FaceDetector getFaceDetector() {
        var provider = aiProperties.getFaceDetection().getProvider();
        return faceDetectorMap.get(provider);
    }

    /**
     * 获取人脸特征提取器实例（从 SPI 加载的 Map 中获取）
     *
     * @return 人脸特征提取器
     */
    private FaceFeature getFaceFeature() {
        var provider = aiProperties.getFaceDetection().getProvider();
        return faceFeatureMap.get(provider);
    }

    /**
     * 获取图像检测器实例（从 SPI 加载的 Map 中获取）
     *
     * @return 图像检测器
     */
    private Detector getImageDetector() {
        var provider = aiProperties.getImage().getProvider();
        String key = "image" + provider;
        return detectorMap.get(key);
    }

    /**
     * 获取图像特征提取器实例（从 SPI 加载的 Map 中获取）
     *
     * @return 图像特征提取器
     */
    private Feature getImageFeature() {
        var provider = aiProperties.getImage().getProvider();
        String key = "image" + provider;
        return featureMap.get(key);
    }

    /**
     * 获取文本特征提取器实例（从 SPI 加载的 Map 中获取）
     *
     * @return 文本特征提取器
     */
    private Feature getTextFeature() {
        var provider = aiProperties.getText().getProvider();
        String key = "text" + provider;
        return featureMap.get(key);
    }

    /**
     * 获取OCR识别器实例（从 SPI 加载的 Map 中获取）
     *
     * @return OCR识别器
     */
    private OcrRecognizer getOcrRecognizer() {
        var provider = aiProperties.getOcr().getProvider();
        return ocrRecognizerMap.get(provider);
    }

    /**
     * 获取版面分析检测器实例（从 SPI 加载的 Map 中获取）
     *
     * @return 版面分析检测器
     */
    private Detector getLayoutDetector() {
        var provider = aiProperties.getImage().getProvider();
        String key = "layout" + provider;
        return detectorMap.get(key);
    }

    /**
     * 获取性别年龄检测器实例（从 SPI 加载的 Map 中获取）
     *
     * @return 性别年龄检测器
     */
    private Detector getGenderAgeDetector() {
        var provider = aiProperties.getFaceDetection().getProvider();
        String key = "genderage" + provider;
        return detectorMap.get(key);
    }

    // ==================== 人脸相关 ====================

    @Override
    public FaceDetectionResult detectFaces(byte[] imageData) {
        if (!aiProperties.isEnabled() || !aiProperties.getFaceDetection().isEnabled()) {
            return FaceDetectionResult.fail("Face detection is disabled");
        }

        var startTime = System.currentTimeMillis();
        try {
            // 使用新的 AiClient 架构
            var faceBuilder = aiClient.createFace();
            var result = (PredictResultObject<PredictResult>) faceBuilder.detect(imageData);
            var faces = convertToFaces(result);
            
            return FaceDetectionResult.success(faces, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("[AI][人脸检测]检测失败", e);
            return FaceDetectionResult.fail(e.getMessage());
        }
    }

    /**
     * 转换检测结果为人脸列表
     *
     * @param result 预测结果
     * @return 人脸列表
     */
    private List<FaceDetectionResult.Face> convertToFaces(PredictResultObject<PredictResult> result) {
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        List<FaceDetectionResult.Face> faces = new ArrayList<>();
        for (var predictResult : result.getList()) {
            if (predictResult instanceof DetectorPredictResult detectorResult) {
                var boundingBox = detectorResult.getBoundingBox();
                if (boundingBox != null && boundingBox.getCorners() != null && !boundingBox.getCorners().isEmpty()) {
                    var topLeft = boundingBox.getCorners().getFirst();
                    faces.add(FaceDetectionResult.Face.builder()
                            .x((int) topLeft.getX())
                            .y((int) topLeft.getY())
                            .width((int) boundingBox.getWidth())
                            .height((int) boundingBox.getHeight())
                            .confidence((float) detectorResult.getConfidence())
                            .build());
                }
            }
        }
        return faces;
    }

    @Override
    public FaceDetectionResult detectFaces(File imageFile) {
        try {
            var imageData = Files.readAllBytes(imageFile.toPath());
            return detectFaces(imageData);
        } catch (IOException e) {
            log.error("[AI][人脸检测]读取文件失败: {}", imageFile.getAbsolutePath(), e);
            return FaceDetectionResult.fail("Failed to read image file: " + e.getMessage());
        }
    }

    @Override
    public FaceDetectionResult detectFaces(InputStream inputStream) {
        try {
            var imageData = toByteArray(inputStream);
            return detectFaces(imageData);
        } catch (IOException e) {
            log.error("[AI][人脸检测]读取输入流失败", e);
            return FaceDetectionResult.fail("Failed to read image from input stream: " + e.getMessage());
        }
    }

    @Override
    public FeatureResult extractFaceFeature(byte[] imageData) {
        if (!aiProperties.isEnabled() || !aiProperties.getFaceDetection().isEnabled()) {
            return FeatureResult.fail("Face feature extraction is disabled");
        }

        var startTime = System.currentTimeMillis();
        try {
            // 使用新的 AiClient 架构
            var faceBuilder = aiClient.createFace();
            var result = (PredictResultObject<PredictResult>) faceBuilder.recognize(imageData);
            
            // 提取特征值（假设第一个结果包含特征）
            float[] featureValue = null;
            if (result != null && !result.isEmpty()) {
                var firstResult = result.getList().getFirst();
                if (firstResult instanceof DetectorPredictResult detectorResult) {
                    // 尝试从结果中提取特征值
                    featureValue = detectorResult.asFloatArray();
                }
            }
            
            return FeatureResult.success(featureValue, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("[AI][人脸特征]特征提取失败", e);
            return FeatureResult.fail(e.getMessage());
        }
    }

    @Override
    public FeatureResult extractFaceFeature(File imageFile) {
        try {
            var imageData = Files.readAllBytes(imageFile.toPath());
            return extractFaceFeature(imageData);
        } catch (IOException e) {
            log.error("[AI][人脸特征]读取文件失败: {}", imageFile.getAbsolutePath(), e);
            return FeatureResult.fail("Failed to read image file: " + e.getMessage());
        }
    }

    @Override
    public GenderAgeResult detectGenderAge(byte[] imageData) {
        if (!aiProperties.isEnabled() || !aiProperties.getFaceDetection().isEnabled()) {
            return GenderAgeResult.fail("Gender age detection is disabled");
        }

        var startTime = System.currentTimeMillis();
        try {
            var detector = getGenderAgeDetector();
            if (detector == null) {
                return GenderAgeResult.fail("GenderAgeDetector not available");
            }

            var result = detector.detect(imageData);
            var genderAges = convertToGenderAge(result);
            
            return GenderAgeResult.success(genderAges, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("[AI][性别年龄]检测失败", e);
            return GenderAgeResult.fail(e.getMessage());
        }
    }

    /**
     * 转换检测结果为性别年龄列表
     *
     * @param result 预测结果
     * @return 性别年龄列表
     */
    private List<GenderAgeResult.GenderAge> convertToGenderAge(PredictResultObject<PredictResult> result) {
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        List<GenderAgeResult.GenderAge> genderAges = new ArrayList<>();
        for (var predictResult : result.getList()) {
            if (predictResult instanceof HumanPredictResult humanResult) {
                var gender = parseGender(humanResult.getGender());
                genderAges.add(GenderAgeResult.GenderAge.builder()
                        .gender(gender)
                        .genderConfidence(humanResult.getGenderConfidence())
                        .age(humanResult.getAge())
                        .ageConfidence(humanResult.getAgeConfidence())
                        .build());
            }
        }
        return genderAges;
    }

    /**
     * 解析性别字符串
     *
     * @param genderStr 性别字符串
     * @return 性别枚举
     */
    private GenderAgeResult.Gender parseGender(String genderStr) {
        if (genderStr == null) {
            return GenderAgeResult.Gender.UNKNOWN;
        }
        return switch (genderStr.toLowerCase()) {
            case "male", "男", "m" -> GenderAgeResult.Gender.MALE;
            case "female", "女", "f" -> GenderAgeResult.Gender.FEMALE;
            default -> GenderAgeResult.Gender.UNKNOWN;
        };
    }

    @Override
    public GenderAgeResult detectGenderAge(File imageFile) {
        try {
            var imageData = Files.readAllBytes(imageFile.toPath());
            return detectGenderAge(imageData);
        } catch (IOException e) {
            log.error("[AI][性别年龄]读取文件失败: {}", imageFile.getAbsolutePath(), e);
            return GenderAgeResult.fail("Failed to read image file: " + e.getMessage());
        }
    }

    // ==================== 图片相关 ====================

    @Override
    public ImageDetectionResult detectImage(byte[] imageData) {
        if (!aiProperties.isEnabled() || !aiProperties.getImage().isEnabled()) {
            return ImageDetectionResult.fail("Image detection is disabled");
        }

        var startTime = System.currentTimeMillis();
        try {
            var detector = getImageDetector();
            if (detector == null) {
                return ImageDetectionResult.fail("ImageDetector not available");
            }

            var result = detector.detect(imageData);
            var objects = convertToDetectedObjects(result);
            
            return ImageDetectionResult.success(objects, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("[AI][图像检测]检测失败", e);
            return ImageDetectionResult.fail(e.getMessage());
        }
    }

    /**
     * 转换检测结果为物体列表
     *
     * @param result 预测结果
     * @return 检测物体列表
     */
    private List<ImageDetectionResult.DetectedObject> convertToDetectedObjects(PredictResultObject<PredictResult> result) {
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        List<ImageDetectionResult.DetectedObject> objects = new ArrayList<>();
        for (var predictResult : result.getList()) {
            if (predictResult instanceof DetectorPredictResult detectorResult) {
                var boundingBox = detectorResult.getBoundingBox();
                if (boundingBox != null && boundingBox.getCorners() != null && !boundingBox.getCorners().isEmpty()) {
                    var topLeft = boundingBox.getCorners().getFirst();
                    objects.add(ImageDetectionResult.DetectedObject.builder()
                            .className(detectorResult.getClassId())
                            .confidence((float) detectorResult.getConfidence())
                            .x((int) topLeft.getX())
                            .y((int) topLeft.getY())
                            .width((int) boundingBox.getWidth())
                            .height((int) boundingBox.getHeight())
                            .build());
                }
            }
        }
        return objects;
    }

    @Override
    public ImageDetectionResult detectImage(File imageFile) {
        try {
            var imageData = Files.readAllBytes(imageFile.toPath());
            return detectImage(imageData);
        } catch (IOException e) {
            log.error("[AI][图像检测]读取文件失败: {}", imageFile.getAbsolutePath(), e);
            return ImageDetectionResult.fail("Failed to read image file: " + e.getMessage());
        }
    }

    @Override
    public FeatureResult extractImageFeature(byte[] imageData) {
        if (!aiProperties.isEnabled() || !aiProperties.getImage().isEnabled()) {
            return FeatureResult.fail("Image feature extraction is disabled");
        }

        var startTime = System.currentTimeMillis();
        try {
            var feature = getImageFeature();
            if (feature == null) {
                return FeatureResult.fail("ImageFeature not available");
            }

            var predictResult = feature.extract(imageData);
            var featureValue = predictResult != null ? predictResult.asFloatArray() : null;
            return FeatureResult.success(featureValue, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("[AI][图像特征]特征提取失败", e);
            return FeatureResult.fail(e.getMessage());
        }
    }

    @Override
    public FeatureResult extractImageFeature(File imageFile) {
        try {
            var imageData = Files.readAllBytes(imageFile.toPath());
            return extractImageFeature(imageData);
        } catch (IOException e) {
            log.error("[AI][图像特征]读取文件失败: {}", imageFile.getAbsolutePath(), e);
            return FeatureResult.fail("Failed to read image file: " + e.getMessage());
        }
    }

    // ==================== 文本相关 ====================

    @Override
    public FeatureResult extractTextFeature(String text) {
        if (!aiProperties.isEnabled() || !aiProperties.getText().isEnabled()) {
            return FeatureResult.fail("Text feature extraction is disabled");
        }

        var startTime = System.currentTimeMillis();
        try {
            var feature = getTextFeature();
            if (feature == null) {
                return FeatureResult.fail("TextFeature not available");
            }

            var predictResult = feature.extract(text);
            var featureValue = predictResult != null ? predictResult.asFloatArray() : null;
            return FeatureResult.success(featureValue, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("[AI][文本特征]特征提取失败", e);
            return FeatureResult.fail(e.getMessage());
        }
    }

    // ==================== OCR相关 ====================

    @Override
    public com.chua.starter.ai.support.model.OcrResult ocr(byte[] imageData) {
        if (!aiProperties.isEnabled() || !aiProperties.getOcr().isEnabled()) {
            return com.chua.starter.ai.support.model.OcrResult.fail("OCR is disabled");
        }

        var startTime = System.currentTimeMillis();
        try {
            // 使用新的 AiClient 架构
            var ocrBuilder = aiClient.createOcr();
            
            // 获取完整文本
            var fullText = ocrBuilder.recognize(imageData);
            
            // 获取详细结果
            var detailResult = (PredictResultObject<PredictResult>) ocrBuilder.recognizeDetail(imageData);
            var textLines = convertToTextLinesFromPredictResult(detailResult);
            
            return com.chua.starter.ai.support.model.OcrResult.success(fullText, textLines, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("[AI][OCR]识别失败", e);
            return com.chua.starter.ai.support.model.OcrResult.fail(e.getMessage());
        }
    }
    
    /**
     * 从预测结果转换为文本行列表
     *
     * @param result 预测结果
     * @return 文本行列表
     */
    private List<com.chua.starter.ai.support.model.OcrResult.TextLine> convertToTextLinesFromPredictResult(PredictResultObject<PredictResult> result) {
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        List<com.chua.starter.ai.support.model.OcrResult.TextLine> textLines = new ArrayList<>();
        for (var predictResult : result.getList()) {
            if (predictResult instanceof DetectorPredictResult detectorResult) {
                textLines.add(com.chua.starter.ai.support.model.OcrResult.TextLine.builder()
                        .text(detectorResult.getClassId())
                        .confidence((float) detectorResult.getConfidence())
                        .build());
            }
        }
        return textLines;
    }

    /**
     * 转换OCR结果为文本行列表
     *
     * @param results OCR识别结果列表
     * @return 文本行列表
     */
    private List<com.chua.starter.ai.support.model.OcrResult.TextLine> convertToTextLines(List<OcrResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        List<com.chua.starter.ai.support.model.OcrResult.TextLine> textLines = new ArrayList<>();
        for (var ocrResult : results) {
            textLines.add(com.chua.starter.ai.support.model.OcrResult.TextLine.builder()
                    .text(ocrResult.getText())
                    .confidence(ocrResult.getConfidence())
                    .build());
        }
        return textLines;
    }

    @Override
    public com.chua.starter.ai.support.model.OcrResult ocr(File imageFile) {
        try {
            var imageData = Files.readAllBytes(imageFile.toPath());
            return ocr(imageData);
        } catch (IOException e) {
            log.error("[AI][OCR]读取文件失败: {}", imageFile.getAbsolutePath(), e);
            return com.chua.starter.ai.support.model.OcrResult.fail("Failed to read image file: " + e.getMessage());
        }
    }

    @Override
    public com.chua.starter.ai.support.model.OcrResult ocr(InputStream inputStream) {
        try {
            var imageData = toByteArray(inputStream);
            return ocr(imageData);
        } catch (IOException e) {
            log.error("[AI][OCR]读取输入流失败", e);
            return com.chua.starter.ai.support.model.OcrResult.fail("Failed to read image from input stream: " + e.getMessage());
        }
    }

    // ==================== 版面分析 ====================

    @Override
    public LayoutAnalysisResult analyzeLayout(byte[] imageData) {
        if (!aiProperties.isEnabled()) {
            return LayoutAnalysisResult.fail("AI module is disabled");
        }

        var startTime = System.currentTimeMillis();
        try {
            var detector = getLayoutDetector();
            if (detector == null) {
                return LayoutAnalysisResult.fail("LayoutDetector not available");
            }

            var result = detector.detect(imageData);
            var elements = convertToLayoutElements(result);
            
            return LayoutAnalysisResult.success(elements, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("[AI][版面分析]分析失败", e);
            return LayoutAnalysisResult.fail(e.getMessage());
        }
    }

    /**
     * 转换检测结果为版面元素列表
     *
     * @param result 预测结果
     * @return 版面元素列表
     */
    private List<LayoutAnalysisResult.LayoutElement> convertToLayoutElements(PredictResultObject<PredictResult> result) {
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        List<LayoutAnalysisResult.LayoutElement> elements = new ArrayList<>();
        var order = 0;
        for (var predictResult : result.getList()) {
            if (predictResult instanceof DetectorPredictResult detectorResult) {
                var boundingBox = detectorResult.getBoundingBox();
                if (boundingBox != null && boundingBox.getCorners() != null && !boundingBox.getCorners().isEmpty()) {
                    var topLeft = boundingBox.getCorners().getFirst();
                    var type = parseElementType(detectorResult.getClassId());
                    
                    elements.add(LayoutAnalysisResult.LayoutElement.builder()
                            .type(type)
                            .confidence((float) detectorResult.getConfidence())
                            .x((int) topLeft.getX())
                            .y((int) topLeft.getY())
                            .width((int) boundingBox.getWidth())
                            .height((int) boundingBox.getHeight())
                            .order(order++)
                            .build());
                }
            }
        }
        return elements;
    }

    /**
     * 解析版面元素类型
     *
     * @param classId 类别标识
     * @return 元素类型枚举
     */
    private LayoutAnalysisResult.ElementType parseElementType(String classId) {
        if (classId == null) {
            return LayoutAnalysisResult.ElementType.OTHER;
        }
        return switch (classId.toLowerCase()) {
            case "text", "文本" -> LayoutAnalysisResult.ElementType.TEXT;
            case "title", "标题" -> LayoutAnalysisResult.ElementType.TITLE;
            case "image", "图片", "figure" -> LayoutAnalysisResult.ElementType.IMAGE;
            case "table", "表格" -> LayoutAnalysisResult.ElementType.TABLE;
            case "list", "列表" -> LayoutAnalysisResult.ElementType.LIST;
            case "formula", "公式" -> LayoutAnalysisResult.ElementType.FORMULA;
            case "header", "页眉" -> LayoutAnalysisResult.ElementType.HEADER;
            case "footer", "页脚" -> LayoutAnalysisResult.ElementType.FOOTER;
            default -> LayoutAnalysisResult.ElementType.OTHER;
        };
    }

    @Override
    public LayoutAnalysisResult analyzeLayout(File imageFile) {
        try {
            var imageData = Files.readAllBytes(imageFile.toPath());
            return analyzeLayout(imageData);
        } catch (IOException e) {
            log.error("[AI][版面分析]读取文件失败: {}", imageFile.getAbsolutePath(), e);
            return LayoutAnalysisResult.fail("Failed to read image file: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 将输入流转换为字节数组
     *
     * @param inputStream 输入流
     * @return 字节数组
     * @throws IOException IO异常
     */
    private byte[] toByteArray(InputStream inputStream) throws IOException {
        var baos = new ByteArrayOutputStream();
        var buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return baos.toByteArray();
    }
}
