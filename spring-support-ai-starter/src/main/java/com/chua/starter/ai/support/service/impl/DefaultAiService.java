package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.deeplearning.support.ml.*;
import com.chua.deeplearning.support.ml.detector.Detector;
import com.chua.deeplearning.support.ml.feature.Feature;
import com.chua.deeplearning.support.ml.function.*;
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
    
    // 深度学习检测器实例
    private volatile FaceDetector faceDetector;
    private volatile FaceFeature faceFeature;
    private volatile ImageDetector imageDetector;
    private volatile ImageFeature imageFeature;
    private volatile TextFeature textFeature;
    private volatile OcrDetector ocrDetector;
    private volatile Detector layoutDetector;
    private volatile Detector genderAgeDetector;

    public DefaultAiService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    // ==================== 获取检测器实例 ====================

    private FaceDetector getFaceDetector() {
        if (faceDetector == null) {
            synchronized (this) {
                if (faceDetector == null) {
                    String provider = aiProperties.getFaceDetection().getProvider();
                    faceDetector = ServiceProvider.of(FaceDetector.class).getNewExtension(provider);
                }
            }
        }
        return faceDetector;
    }

    private FaceFeature getFaceFeature() {
        if (faceFeature == null) {
            synchronized (this) {
                if (faceFeature == null) {
                    String provider = aiProperties.getFaceDetection().getProvider();
                    faceFeature = ServiceProvider.of(FaceFeature.class).getNewExtension(provider);
                }
            }
        }
        return faceFeature;
    }

    private ImageDetector getImageDetector() {
        if (imageDetector == null) {
            synchronized (this) {
                if (imageDetector == null) {
                    String provider = aiProperties.getImage().getProvider();
                    imageDetector = ServiceProvider.of(ImageDetector.class).getNewExtension(provider);
                }
            }
        }
        return imageDetector;
    }

    private ImageFeature getImageFeature() {
        if (imageFeature == null) {
            synchronized (this) {
                if (imageFeature == null) {
                    String provider = aiProperties.getImage().getProvider();
                    imageFeature = ServiceProvider.of(ImageFeature.class).getNewExtension(provider);
                }
            }
        }
        return imageFeature;
    }

    private TextFeature getTextFeature() {
        if (textFeature == null) {
            synchronized (this) {
                if (textFeature == null) {
                    String provider = aiProperties.getText().getProvider();
                    textFeature = ServiceProvider.of(TextFeature.class).getNewExtension(provider);
                }
            }
        }
        return textFeature;
    }

    private OcrDetector getOcrDetector() {
        if (ocrDetector == null) {
            synchronized (this) {
                if (ocrDetector == null) {
                    String provider = aiProperties.getOcr().getProvider();
                    ocrDetector = ServiceProvider.of(OcrDetector.class).getNewExtension(provider);
                }
            }
        }
        return ocrDetector;
    }

    private Detector getLayoutDetector() {
        if (layoutDetector == null) {
            synchronized (this) {
                if (layoutDetector == null) {
                    String provider = aiProperties.getImage().getProvider();
                    layoutDetector = ServiceProvider.of(Detector.class).getNewExtension("layout-" + provider);
                }
            }
        }
        return layoutDetector;
    }

    private Detector getGenderAgeDetector() {
        if (genderAgeDetector == null) {
            synchronized (this) {
                if (genderAgeDetector == null) {
                    String provider = aiProperties.getFaceDetection().getProvider();
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

        long startTime = System.currentTimeMillis();
        try {
            FaceDetector detector = getFaceDetector();
            if (detector == null) {
                return FaceDetectionResult.fail("FaceDetector not available");
            }

            PredictResultObject<PredictResult> result = detector.predict(imageData);
            List<FaceDetectionResult.Face> faces = convertToFaces(result);
            
            return FaceDetectionResult.success(faces, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Face detection failed", e);
            return FaceDetectionResult.fail(e.getMessage());
        }
    }

    /**
     * 转换检测结果为人脸列表
     */
    private List<FaceDetectionResult.Face> convertToFaces(PredictResultObject<PredictResult> result) {
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        List<FaceDetectionResult.Face> faces = new ArrayList<>();
        for (PredictResult predictResult : result.getList()) {
            if (predictResult instanceof DetectorPredictResult detectorResult) {
                var boundingBox = detectorResult.getBoundingBox();
                if (boundingBox != null && boundingBox.getCorners() != null && !boundingBox.getCorners().isEmpty()) {
                    var topLeft = boundingBox.getCorners().get(0);
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
            byte[] imageData = Files.readAllBytes(imageFile.toPath());
            return detectFaces(imageData);
        } catch (IOException e) {
            log.error("Failed to read image file: {}", imageFile.getAbsolutePath(), e);
            return FaceDetectionResult.fail("Failed to read image file: " + e.getMessage());
        }
    }

    @Override
    public FaceDetectionResult detectFaces(InputStream inputStream) {
        try {
            byte[] imageData = toByteArray(inputStream);
            return detectFaces(imageData);
        } catch (IOException e) {
            log.error("Failed to read image from input stream", e);
            return FaceDetectionResult.fail("Failed to read image from input stream: " + e.getMessage());
        }
    }

    @Override
    public FeatureResult extractFaceFeature(byte[] imageData) {
        if (!aiProperties.isEnabled() || !aiProperties.getFaceDetection().isEnabled()) {
            return FeatureResult.fail("Face feature extraction is disabled");
        }

        long startTime = System.currentTimeMillis();
        try {
            FaceFeature feature = getFaceFeature();
            if (feature == null) {
                return FeatureResult.fail("FaceFeature not available");
            }

            float[] featureValue = feature.feature(imageData);
            return FeatureResult.success(featureValue, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Face feature extraction failed", e);
            return FeatureResult.fail(e.getMessage());
        }
    }

    @Override
    public FeatureResult extractFaceFeature(File imageFile) {
        try {
            byte[] imageData = Files.readAllBytes(imageFile.toPath());
            return extractFaceFeature(imageData);
        } catch (IOException e) {
            log.error("Failed to read image file: {}", imageFile.getAbsolutePath(), e);
            return FeatureResult.fail("Failed to read image file: " + e.getMessage());
        }
    }

    @Override
    public GenderAgeResult detectGenderAge(byte[] imageData) {
        if (!aiProperties.isEnabled() || !aiProperties.getFaceDetection().isEnabled()) {
            return GenderAgeResult.fail("Gender age detection is disabled");
        }

        long startTime = System.currentTimeMillis();
        try {
            Detector detector = getGenderAgeDetector();
            if (detector == null) {
                return GenderAgeResult.fail("GenderAgeDetector not available");
            }

            PredictResultObject<PredictResult> result = detector.predict(imageData);
            List<GenderAgeResult.GenderAge> genderAges = convertToGenderAge(result);
            
            return GenderAgeResult.success(genderAges, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Gender age detection failed", e);
            return GenderAgeResult.fail(e.getMessage());
        }
    }

    /**
     * 转换检测结果为性别年龄列表
     */
    private List<GenderAgeResult.GenderAge> convertToGenderAge(PredictResultObject<PredictResult> result) {
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        List<GenderAgeResult.GenderAge> genderAges = new ArrayList<>();
        for (PredictResult predictResult : result.getList()) {
            if (predictResult instanceof GenderPredictResult genderResult) {
                GenderAgeResult.Gender gender = "Male".equalsIgnoreCase(genderResult.getGender()) || "男".equals(genderResult.getGender())
                        ? GenderAgeResult.Gender.MALE
                        : "Female".equalsIgnoreCase(genderResult.getGender()) || "女".equals(genderResult.getGender())
                        ? GenderAgeResult.Gender.FEMALE
                        : GenderAgeResult.Gender.UNKNOWN;

                genderAges.add(GenderAgeResult.GenderAge.builder()
                        .gender(gender)
                        .genderConfidence((float) genderResult.getConfidence())
                        .age(genderResult.getAge())
                        .ageConfidence((float) genderResult.getConfidence())
                        .build());
            }
        }
        return genderAges;
    }

    @Override
    public GenderAgeResult detectGenderAge(File imageFile) {
        try {
            byte[] imageData = Files.readAllBytes(imageFile.toPath());
            return detectGenderAge(imageData);
        } catch (IOException e) {
            log.error("Failed to read image file: {}", imageFile.getAbsolutePath(), e);
            return GenderAgeResult.fail("Failed to read image file: " + e.getMessage());
        }
    }

    // ==================== 图片相关 ====================

    @Override
    public ImageDetectionResult detectImage(byte[] imageData) {
        if (!aiProperties.isEnabled() || !aiProperties.getImage().isEnabled()) {
            return ImageDetectionResult.fail("Image detection is disabled");
        }

        long startTime = System.currentTimeMillis();
        try {
            ImageDetector detector = getImageDetector();
            if (detector == null) {
                return ImageDetectionResult.fail("ImageDetector not available");
            }

            PredictResultObject<PredictResult> result = detector.predict(imageData);
            List<ImageDetectionResult.DetectedObject> objects = convertToDetectedObjects(result);
            
            return ImageDetectionResult.success(objects, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Image detection failed", e);
            return ImageDetectionResult.fail(e.getMessage());
        }
    }

    /**
     * 转换检测结果为物体列表
     */
    private List<ImageDetectionResult.DetectedObject> convertToDetectedObjects(PredictResultObject<PredictResult> result) {
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        List<ImageDetectionResult.DetectedObject> objects = new ArrayList<>();
        for (PredictResult predictResult : result.getList()) {
            if (predictResult instanceof DetectorPredictResult detectorResult) {
                var boundingBox = detectorResult.getBoundingBox();
                if (boundingBox != null && boundingBox.getCorners() != null && !boundingBox.getCorners().isEmpty()) {
                    var topLeft = boundingBox.getCorners().get(0);
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
            byte[] imageData = Files.readAllBytes(imageFile.toPath());
            return detectImage(imageData);
        } catch (IOException e) {
            log.error("Failed to read image file: {}", imageFile.getAbsolutePath(), e);
            return ImageDetectionResult.fail("Failed to read image file: " + e.getMessage());
        }
    }

    @Override
    public FeatureResult extractImageFeature(byte[] imageData) {
        if (!aiProperties.isEnabled() || !aiProperties.getImage().isEnabled()) {
            return FeatureResult.fail("Image feature extraction is disabled");
        }

        long startTime = System.currentTimeMillis();
        try {
            ImageFeature feature = getImageFeature();
            if (feature == null) {
                return FeatureResult.fail("ImageFeature not available");
            }

            float[] featureValue = feature.feature(imageData);
            return FeatureResult.success(featureValue, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Image feature extraction failed", e);
            return FeatureResult.fail(e.getMessage());
        }
    }

    @Override
    public FeatureResult extractImageFeature(File imageFile) {
        try {
            byte[] imageData = Files.readAllBytes(imageFile.toPath());
            return extractImageFeature(imageData);
        } catch (IOException e) {
            log.error("Failed to read image file: {}", imageFile.getAbsolutePath(), e);
            return FeatureResult.fail("Failed to read image file: " + e.getMessage());
        }
    }

    // ==================== 文本相关 ====================

    @Override
    public FeatureResult extractTextFeature(String text) {
        if (!aiProperties.isEnabled() || !aiProperties.getText().isEnabled()) {
            return FeatureResult.fail("Text feature extraction is disabled");
        }

        long startTime = System.currentTimeMillis();
        try {
            TextFeature feature = getTextFeature();
            if (feature == null) {
                return FeatureResult.fail("TextFeature not available");
            }

            float[] featureValue = feature.feature(text);
            return FeatureResult.success(featureValue, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Text feature extraction failed", e);
            return FeatureResult.fail(e.getMessage());
        }
    }

    // ==================== OCR相关 ====================

    @Override
    public OcrResult ocr(byte[] imageData) {
        if (!aiProperties.isEnabled() || !aiProperties.getOcr().isEnabled()) {
            return OcrResult.fail("OCR is disabled");
        }

        long startTime = System.currentTimeMillis();
        try {
            OcrDetector detector = getOcrDetector();
            if (detector == null) {
                return OcrResult.fail("OcrDetector not available");
            }

            String fullText = detector.getText(imageData);
            PredictResultObject<PredictResult> result = detector.predict(imageData);
            List<OcrResult.TextLine> textLines = convertToTextLines(result);
            
            return OcrResult.success(fullText, textLines, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("OCR failed", e);
            return OcrResult.fail(e.getMessage());
        }
    }

    /**
     * 转换检测结果为文本行列表
     */
    private List<OcrResult.TextLine> convertToTextLines(PredictResultObject<PredictResult> result) {
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        List<OcrResult.TextLine> textLines = new ArrayList<>();
        for (PredictResult predictResult : result.getList()) {
            if (predictResult instanceof LabelPredictResult labelResult) {
                textLines.add(OcrResult.TextLine.builder()
                        .text(labelResult.asString())
                        .confidence((float) labelResult.getConfidence())
                        .build());
            }
        }
        return textLines;
    }

    @Override
    public OcrResult ocr(File imageFile) {
        try {
            byte[] imageData = Files.readAllBytes(imageFile.toPath());
            return ocr(imageData);
        } catch (IOException e) {
            log.error("Failed to read image file: {}", imageFile.getAbsolutePath(), e);
            return OcrResult.fail("Failed to read image file: " + e.getMessage());
        }
    }

    @Override
    public OcrResult ocr(InputStream inputStream) {
        try {
            byte[] imageData = toByteArray(inputStream);
            return ocr(imageData);
        } catch (IOException e) {
            log.error("Failed to read image from input stream", e);
            return OcrResult.fail("Failed to read image from input stream: " + e.getMessage());
        }
    }

    // ==================== 版面分析 ====================

    @Override
    public LayoutAnalysisResult analyzeLayout(byte[] imageData) {
        if (!aiProperties.isEnabled()) {
            return LayoutAnalysisResult.fail("AI module is disabled");
        }

        long startTime = System.currentTimeMillis();
        try {
            Detector detector = getLayoutDetector();
            if (detector == null) {
                return LayoutAnalysisResult.fail("LayoutDetector not available");
            }

            PredictResultObject<PredictResult> result = detector.predict(imageData);
            List<LayoutAnalysisResult.LayoutElement> elements = convertToLayoutElements(result);
            
            return LayoutAnalysisResult.success(elements, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("Layout analysis failed", e);
            return LayoutAnalysisResult.fail(e.getMessage());
        }
    }

    /**
     * 转换检测结果为版面元素列表
     */
    private List<LayoutAnalysisResult.LayoutElement> convertToLayoutElements(PredictResultObject<PredictResult> result) {
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        List<LayoutAnalysisResult.LayoutElement> elements = new ArrayList<>();
        int order = 0;
        for (PredictResult predictResult : result.getList()) {
            if (predictResult instanceof DetectorPredictResult detectorResult) {
                var boundingBox = detectorResult.getBoundingBox();
                if (boundingBox != null && boundingBox.getCorners() != null && !boundingBox.getCorners().isEmpty()) {
                    var topLeft = boundingBox.getCorners().get(0);
                    LayoutAnalysisResult.ElementType type = parseElementType(detectorResult.getClassId());
                    
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
     * 解析元素类型
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
            byte[] imageData = Files.readAllBytes(imageFile.toPath());
            return analyzeLayout(imageData);
        } catch (IOException e) {
            log.error("Failed to read image file: {}", imageFile.getAbsolutePath(), e);
            return LayoutAnalysisResult.fail("Failed to read image file: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private byte[] toByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return baos.toByteArray();
    }
}
