package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.core.utils.ServiceProvider;
import com.chua.deeplearning.support.core.function.api.Detector;
import com.chua.deeplearning.support.core.function.api.Feature;
import com.chua.deeplearning.support.core.result.DetectorPredictResult;
import com.chua.deeplearning.support.core.result.HumanPredictResult;
import com.chua.deeplearning.support.core.result.PredictResult;
import com.chua.deeplearning.support.core.result.PredictResultObject;
import com.chua.deeplearning.support.ml.face.api.FaceDetector;
import com.chua.deeplearning.support.ml.face.api.FaceFeature;
import com.chua.deeplearning.support.ml.ocr.api.OcrRecognizer;
import com.chua.deeplearning.support.ml.ocr.model.OcrResult;
import com.chua.starter.ai.support.model.*;
import com.chua.starter.ai.support.properties.AiProperties;
import com.chua.starter.ai.support.service.AiService;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
     * 人脸检测器实例
     */
    private volatile FaceDetector faceDetector;
    
    /**
     * 人脸特征提取器实例
     */
    private volatile FaceFeature faceFeature;
    
    /**
     * 图像检测器实例
     */
    private volatile Detector imageDetector;
    
    /**
     * 图像特征提取器实例
     */
    private volatile Feature imageFeature;
    
    /**
     * 文本特征提取器实例
     */
    private volatile Feature textFeature;
    
    /**
     * OCR识别器实例
     */
    private volatile OcrRecognizer ocrRecognizer;
    
    /**
     * 版面分析检测器实例
     */
    private volatile Detector layoutDetector;
    
    /**
     * 性别年龄检测器实例
     */
    private volatile Detector genderAgeDetector;

    /**
     * 构造函数
     *
     * @param aiProperties AI配置属性
     */
    public DefaultAiService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    // ==================== 获取检测器实例 ====================

    /**
     * 获取人脸检测器实例（懒加载）
     *
     * @return 人脸检测器
     */
    private FaceDetector getFaceDetector() {
        if (faceDetector == null) {
            synchronized (this) {
                if (faceDetector == null) {
                    var provider = aiProperties.getFaceDetection().getProvider();
                    faceDetector = ServiceProvider.of(FaceDetector.class).getNewExtension(provider);
                }
            }
        }
        return faceDetector;
    }

    /**
     * 获取人脸特征提取器实例（懒加载）
     *
     * @return 人脸特征提取器
     */
    private FaceFeature getFaceFeature() {
        if (faceFeature == null) {
            synchronized (this) {
                if (faceFeature == null) {
                    var provider = aiProperties.getFaceDetection().getProvider();
                    faceFeature = ServiceProvider.of(FaceFeature.class).getNewExtension(provider);
                }
            }
        }
        return faceFeature;
    }

    /**
     * 获取图像检测器实例（懒加载）
     *
     * @return 图像检测器
     */
    private Detector getImageDetector() {
        if (imageDetector == null) {
            synchronized (this) {
                if (imageDetector == null) {
                    var provider = aiProperties.getImage().getProvider();
                    imageDetector = ServiceProvider.of(Detector.class).getNewExtension("image-" + provider);
                }
            }
        }
        return imageDetector;
    }

    /**
     * 获取图像特征提取器实例（懒加载）
     *
     * @return 图像特征提取器
     */
    private Feature getImageFeature() {
        if (imageFeature == null) {
            synchronized (this) {
                if (imageFeature == null) {
                    var provider = aiProperties.getImage().getProvider();
                    imageFeature = ServiceProvider.of(Feature.class).getNewExtension("image-" + provider);
                }
            }
        }
        return imageFeature;
    }

    /**
     * 获取文本特征提取器实例（懒加载）
     *
     * @return 文本特征提取器
     */
    private Feature getTextFeature() {
        if (textFeature == null) {
            synchronized (this) {
                if (textFeature == null) {
                    var provider = aiProperties.getText().getProvider();
                    textFeature = ServiceProvider.of(Feature.class).getNewExtension("text-" + provider);
                }
            }
        }
        return textFeature;
    }

    /**
     * 获取OCR识别器实例（懒加载）
     *
     * @return OCR识别器
     */
    private OcrRecognizer getOcrRecognizer() {
        if (ocrRecognizer == null) {
            synchronized (this) {
                if (ocrRecognizer == null) {
                    var provider = aiProperties.getOcr().getProvider();
                    ocrRecognizer = ServiceProvider.of(OcrRecognizer.class).getNewExtension(provider);
                }
            }
        }
        return ocrRecognizer;
    }

    /**
     * 获取版面分析检测器实例（懒加载）
     *
     * @return 版面分析检测器
     */
    private Detector getLayoutDetector() {
        if (layoutDetector == null) {
            synchronized (this) {
                if (layoutDetector == null) {
                    var provider = aiProperties.getImage().getProvider();
                    layoutDetector = ServiceProvider.of(Detector.class).getNewExtension("layout-" + provider);
                }
            }
        }
        return layoutDetector;
    }

    /**
     * 获取性别年龄检测器实例（懒加载）
     *
     * @return 性别年龄检测器
     */
    private Detector getGenderAgeDetector() {
        if (genderAgeDetector == null) {
            synchronized (this) {
                if (genderAgeDetector == null) {
                    var provider = aiProperties.getFaceDetection().getProvider();
                    genderAgeDetector = ServiceProvider.of(Detector.class).getNewExtension("genderAge-" + provider);
                }
            }
        }
        return genderAgeDetector;
    }

    // ==================== 人脸相关 ====================

    @Override
    public FaceDetectionResult detectFaces(byte[] imageData) {
        if (!aiProperties.isEnabled() || !aiProperties.getFaceDetection().isEnabled()) {
            return FaceDetectionResult.fail("Face detection is disabled");
        }

        var startTime = System.currentTimeMillis();
        try {
            var detector = getFaceDetector();
            if (detector == null) {
                return FaceDetectionResult.fail("FaceDetector not available");
            }

            var result = detector.detect(imageData);
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
            var feature = getFaceFeature();
            if (feature == null) {
                return FeatureResult.fail("FaceFeature not available");
            }

            var featureValue = feature.feature(imageData);
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
            var recognizer = getOcrRecognizer();
            if (recognizer == null) {
                return com.chua.starter.ai.support.model.OcrResult.fail("OcrRecognizer not available");
            }

            var fullText = recognizer.getText(imageData);
            var results = recognizer.recognizeAll(imageData);
            var textLines = convertToTextLines(results);
            
            return com.chua.starter.ai.support.model.OcrResult.success(fullText, textLines, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("[AI][OCR]识别失败", e);
            return com.chua.starter.ai.support.model.OcrResult.fail(e.getMessage());
        }
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
