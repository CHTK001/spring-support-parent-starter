package com.chua.starter.ai.support.chat;

import com.chua.starter.ai.support.model.*;
import com.chua.starter.ai.support.service.AiService;
import com.chua.starter.ai.support.service.AsyncAiService;
import com.chua.starter.ai.support.service.ReactiveAiService;
import com.chua.starter.ai.support.service.impl.DefaultAsyncAiService;
import com.chua.starter.ai.support.service.impl.DefaultReactiveAiService;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * AI链式聊天对象
 * <p>
 * 支持链式调用的AI服务封装，提供便捷的API调用方式
 *
 * <pre>
 * 使用示例:
 * {@code
 * // 人脸检测
 * FaceDetectionResult result = AiChat.of(aiService)
 *     .image(imageBytes)
 *     .detectFaces();
 *
 * // 人脸特征提取
 * FeatureResult feature = AiChat.of(aiService)
 *     .image(imageFile)
 *     .faceFeature();
 *
 * // 图片检测
 * ImageDetectionResult objects = AiChat.of(aiService)
 *     .image(imageBytes)
 *     .detectImage();
 *
 * // OCR识别
 * OcrResult ocrResult = AiChat.of(aiService)
 *     .image(imageFile)
 *     .ocr();
 *
 * // 文本特征
 * FeatureResult textFeature = AiChat.of(aiService)
 *     .text("Hello World")
 *     .textFeature();
 *
 * // 版面分析
 * LayoutAnalysisResult layout = AiChat.of(aiService)
 *     .image(imageBytes)
 *     .analyzeLayout();
 *
 * // 性别年龄检测
 * GenderAgeResult genderAge = AiChat.of(aiService)
 *     .image(imageFile)
 *     .detectGenderAge();
 * }
 * </pre>
 *
 * @author CH
 * @since 2024-01-01
 */
public class AiChat {

    private final AiService aiService;
    private final AsyncAiService asyncAiService;
    private final ReactiveAiService reactiveAiService;
    
    private byte[] imageData;
    private File imageFile;
    private InputStream imageStream;
    private String textContent;

    private AiChat(AiService aiService, AsyncAiService asyncAiService, ReactiveAiService reactiveAiService) {
        this.aiService = aiService;
        this.asyncAiService = asyncAiService;
        this.reactiveAiService = reactiveAiService;
    }

    /**
     * 创建AiChat实例（同步模式）
     *
     * @param aiService AI服务实例
     * @return AiChat实例
     */
    public static AiChat of(AiService aiService) {
        return new AiChat(aiService, new DefaultAsyncAiService(aiService), null);
    }

    /**
     * 创建AiChat实例（带自定义执行器）
     *
     * @param aiService AI服务实例
     * @param executor  异步执行器
     * @return AiChat实例
     */
    public static AiChat of(AiService aiService, Executor executor) {
        return new AiChat(aiService, new DefaultAsyncAiService(aiService, executor), null);
    }

    /**
     * 创建AiChat实例（完整模式）
     *
     * @param aiService         AI服务实例
     * @param asyncAiService    异步AI服务实例
     * @param reactiveAiService 响应式AI服务实例
     * @return AiChat实例
     */
    public static AiChat of(AiService aiService, AsyncAiService asyncAiService, ReactiveAiService reactiveAiService) {
        return new AiChat(aiService, asyncAiService, reactiveAiService);
    }

    /**
     * 启用Reactor响应式支持
     *
     * @return 当前实例（启用Reactor支持）
     */
    public AiChat withReactor() {
        if (this.reactiveAiService != null) {
            return this;
        }
        return new AiChat(this.aiService, this.asyncAiService, new DefaultReactiveAiService(this.aiService));
    }

    /**
     * 设置图片数据（字节数组）
     *
     * @param imageData 图片字节数据
     * @return 当前实例
     */
    public AiChat image(byte[] imageData) {
        this.imageData = imageData;
        this.imageFile = null;
        this.imageStream = null;
        return this;
    }

    /**
     * 设置图片文件
     *
     * @param imageFile 图片文件
     * @return 当前实例
     */
    public AiChat image(File imageFile) {
        this.imageFile = imageFile;
        this.imageData = null;
        this.imageStream = null;
        return this;
    }

    /**
     * 设置图片输入流
     *
     * @param inputStream 图片输入流
     * @return 当前实例
     */
    public AiChat image(InputStream inputStream) {
        this.imageStream = inputStream;
        this.imageData = null;
        this.imageFile = null;
        return this;
    }

    /**
     * 设置文本内容
     *
     * @param text 文本内容
     * @return 当前实例
     */
    public AiChat text(String text) {
        this.textContent = text;
        return this;
    }

    // ==================== 同步操作 ====================

    /**
     * 执行人脸检测（同步）
     *
     * @return 人脸检测结果
     */
    public FaceDetectionResult detectFaces() {
        byte[] data = getImageData();
        if (data != null) {
            return aiService.detectFaces(data);
        }
        if (imageFile != null) {
            return aiService.detectFaces(imageFile);
        }
        if (imageStream != null) {
            return aiService.detectFaces(imageStream);
        }
        return FaceDetectionResult.fail("No image data provided");
    }

    /**
     * 提取人脸特征值（同步）
     *
     * @return 人脸特征值结果
     */
    public FeatureResult faceFeature() {
        byte[] data = getImageData();
        if (data != null) {
            return aiService.extractFaceFeature(data);
        }
        if (imageFile != null) {
            return aiService.extractFaceFeature(imageFile);
        }
        return FeatureResult.fail("No image data provided");
    }

    /**
     * 检测性别年龄（同步）
     *
     * @return 性别年龄检测结果
     */
    public GenderAgeResult detectGenderAge() {
        byte[] data = getImageData();
        if (data != null) {
            return aiService.detectGenderAge(data);
        }
        if (imageFile != null) {
            return aiService.detectGenderAge(imageFile);
        }
        return GenderAgeResult.fail("No image data provided");
    }

    /**
     * 执行图片物体检测（同步）
     *
     * @return 图片检测结果
     */
    public ImageDetectionResult detectImage() {
        byte[] data = getImageData();
        if (data != null) {
            return aiService.detectImage(data);
        }
        if (imageFile != null) {
            return aiService.detectImage(imageFile);
        }
        return ImageDetectionResult.fail("No image data provided");
    }

    /**
     * 提取图片特征值（同步）
     *
     * @return 图片特征值结果
     */
    public FeatureResult imageFeature() {
        byte[] data = getImageData();
        if (data != null) {
            return aiService.extractImageFeature(data);
        }
        if (imageFile != null) {
            return aiService.extractImageFeature(imageFile);
        }
        return FeatureResult.fail("No image data provided");
    }

    /**
     * 提取文本特征值（同步）
     *
     * @return 文本特征值结果
     */
    public FeatureResult textFeature() {
        if (textContent == null || textContent.isEmpty()) {
            return FeatureResult.fail("No text content provided");
        }
        return aiService.extractTextFeature(textContent);
    }

    /**
     * 执行OCR文字识别（同步）
     *
     * @return OCR识别结果
     */
    public OcrResult ocr() {
        byte[] data = getImageData();
        if (data != null) {
            return aiService.ocr(data);
        }
        if (imageFile != null) {
            return aiService.ocr(imageFile);
        }
        if (imageStream != null) {
            return aiService.ocr(imageStream);
        }
        return OcrResult.fail("No image data provided");
    }

    /**
     * 执行版面分析（同步）
     *
     * @return 版面分析结果
     */
    public LayoutAnalysisResult analyzeLayout() {
        byte[] data = getImageData();
        if (data != null) {
            return aiService.analyzeLayout(data);
        }
        if (imageFile != null) {
            return aiService.analyzeLayout(imageFile);
        }
        return LayoutAnalysisResult.fail("No image data provided");
    }

    // ==================== 异步操作 (CompletableFuture) ====================

    /**
     * 异步人脸检测
     *
     * @return 人脸检测结果Future
     */
    public CompletableFuture<FaceDetectionResult> detectFacesAsync() {
        byte[] data = getImageData();
        if (data != null) {
            return asyncAiService.detectFacesAsync(data);
        }
        if (imageFile != null) {
            return asyncAiService.detectFacesAsync(imageFile);
        }
        if (imageStream != null) {
            return asyncAiService.detectFacesAsync(imageStream);
        }
        return CompletableFuture.completedFuture(FaceDetectionResult.fail("No image data provided"));
    }

    /**
     * 异步提取人脸特征值
     *
     * @return 人脸特征值结果Future
     */
    public CompletableFuture<FeatureResult> faceFeatureAsync() {
        byte[] data = getImageData();
        if (data != null) {
            return asyncAiService.extractFaceFeatureAsync(data);
        }
        if (imageFile != null) {
            return asyncAiService.extractFaceFeatureAsync(imageFile);
        }
        return CompletableFuture.completedFuture(FeatureResult.fail("No image data provided"));
    }

    /**
     * 异步检测性别年龄
     *
     * @return 性别年龄检测结果Future
     */
    public CompletableFuture<GenderAgeResult> detectGenderAgeAsync() {
        byte[] data = getImageData();
        if (data != null) {
            return asyncAiService.detectGenderAgeAsync(data);
        }
        if (imageFile != null) {
            return asyncAiService.detectGenderAgeAsync(imageFile);
        }
        return CompletableFuture.completedFuture(GenderAgeResult.fail("No image data provided"));
    }

    /**
     * 异步图片物体检测
     *
     * @return 图片检测结果Future
     */
    public CompletableFuture<ImageDetectionResult> detectImageAsync() {
        byte[] data = getImageData();
        if (data != null) {
            return asyncAiService.detectImageAsync(data);
        }
        if (imageFile != null) {
            return asyncAiService.detectImageAsync(imageFile);
        }
        return CompletableFuture.completedFuture(ImageDetectionResult.fail("No image data provided"));
    }

    /**
     * 异步提取图片特征值
     *
     * @return 图片特征值结果Future
     */
    public CompletableFuture<FeatureResult> imageFeatureAsync() {
        byte[] data = getImageData();
        if (data != null) {
            return asyncAiService.extractImageFeatureAsync(data);
        }
        if (imageFile != null) {
            return asyncAiService.extractImageFeatureAsync(imageFile);
        }
        return CompletableFuture.completedFuture(FeatureResult.fail("No image data provided"));
    }

    /**
     * 异步提取文本特征值
     *
     * @return 文本特征值结果Future
     */
    public CompletableFuture<FeatureResult> textFeatureAsync() {
        if (textContent == null || textContent.isEmpty()) {
            return CompletableFuture.completedFuture(FeatureResult.fail("No text content provided"));
        }
        return asyncAiService.extractTextFeatureAsync(textContent);
    }

    /**
     * 异步OCR文字识别
     *
     * @return OCR识别结果Future
     */
    public CompletableFuture<OcrResult> ocrAsync() {
        byte[] data = getImageData();
        if (data != null) {
            return asyncAiService.ocrAsync(data);
        }
        if (imageFile != null) {
            return asyncAiService.ocrAsync(imageFile);
        }
        if (imageStream != null) {
            return asyncAiService.ocrAsync(imageStream);
        }
        return CompletableFuture.completedFuture(OcrResult.fail("No image data provided"));
    }

    /**
     * 异步版面分析
     *
     * @return 版面分析结果Future
     */
    public CompletableFuture<LayoutAnalysisResult> analyzeLayoutAsync() {
        byte[] data = getImageData();
        if (data != null) {
            return asyncAiService.analyzeLayoutAsync(data);
        }
        if (imageFile != null) {
            return asyncAiService.analyzeLayoutAsync(imageFile);
        }
        return CompletableFuture.completedFuture(LayoutAnalysisResult.fail("No image data provided"));
    }

    // ==================== Reactor响应式操作 (Mono) ====================

    /**
     * 响应式人脸检测
     *
     * @return 人脸检测结果Mono
     */
    public Mono<FaceDetectionResult> detectFacesMono() {
        ensureReactiveSupport();
        byte[] data = getImageData();
        if (data != null) {
            return reactiveAiService.detectFacesMono(data);
        }
        if (imageFile != null) {
            return reactiveAiService.detectFacesMono(imageFile);
        }
        if (imageStream != null) {
            return reactiveAiService.detectFacesMono(imageStream);
        }
        return Mono.just(FaceDetectionResult.fail("No image data provided"));
    }

    /**
     * 响应式提取人脸特征值
     *
     * @return 人脸特征值结果Mono
     */
    public Mono<FeatureResult> faceFeatureMono() {
        ensureReactiveSupport();
        byte[] data = getImageData();
        if (data != null) {
            return reactiveAiService.extractFaceFeatureMono(data);
        }
        if (imageFile != null) {
            return reactiveAiService.extractFaceFeatureMono(imageFile);
        }
        return Mono.just(FeatureResult.fail("No image data provided"));
    }

    /**
     * 响应式检测性别年龄
     *
     * @return 性别年龄检测结果Mono
     */
    public Mono<GenderAgeResult> detectGenderAgeMono() {
        ensureReactiveSupport();
        byte[] data = getImageData();
        if (data != null) {
            return reactiveAiService.detectGenderAgeMono(data);
        }
        if (imageFile != null) {
            return reactiveAiService.detectGenderAgeMono(imageFile);
        }
        return Mono.just(GenderAgeResult.fail("No image data provided"));
    }

    /**
     * 响应式图片物体检测
     *
     * @return 图片检测结果Mono
     */
    public Mono<ImageDetectionResult> detectImageMono() {
        ensureReactiveSupport();
        byte[] data = getImageData();
        if (data != null) {
            return reactiveAiService.detectImageMono(data);
        }
        if (imageFile != null) {
            return reactiveAiService.detectImageMono(imageFile);
        }
        return Mono.just(ImageDetectionResult.fail("No image data provided"));
    }

    /**
     * 响应式提取图片特征值
     *
     * @return 图片特征值结果Mono
     */
    public Mono<FeatureResult> imageFeatureMono() {
        ensureReactiveSupport();
        byte[] data = getImageData();
        if (data != null) {
            return reactiveAiService.extractImageFeatureMono(data);
        }
        if (imageFile != null) {
            return reactiveAiService.extractImageFeatureMono(imageFile);
        }
        return Mono.just(FeatureResult.fail("No image data provided"));
    }

    /**
     * 响应式提取文本特征值
     *
     * @return 文本特征值结果Mono
     */
    public Mono<FeatureResult> textFeatureMono() {
        ensureReactiveSupport();
        if (textContent == null || textContent.isEmpty()) {
            return Mono.just(FeatureResult.fail("No text content provided"));
        }
        return reactiveAiService.extractTextFeatureMono(textContent);
    }

    /**
     * 响应式OCR文字识别
     *
     * @return OCR识别结果Mono
     */
    public Mono<OcrResult> ocrMono() {
        ensureReactiveSupport();
        byte[] data = getImageData();
        if (data != null) {
            return reactiveAiService.ocrMono(data);
        }
        if (imageFile != null) {
            return reactiveAiService.ocrMono(imageFile);
        }
        if (imageStream != null) {
            return reactiveAiService.ocrMono(imageStream);
        }
        return Mono.just(OcrResult.fail("No image data provided"));
    }

    /**
     * 响应式版面分析
     *
     * @return 版面分析结果Mono
     */
    public Mono<LayoutAnalysisResult> analyzeLayoutMono() {
        ensureReactiveSupport();
        byte[] data = getImageData();
        if (data != null) {
            return reactiveAiService.analyzeLayoutMono(data);
        }
        if (imageFile != null) {
            return reactiveAiService.analyzeLayoutMono(imageFile);
        }
        return Mono.just(LayoutAnalysisResult.fail("No image data provided"));
    }

    /**
     * 确保响应式支持已启用
     */
    private void ensureReactiveSupport() {
        if (reactiveAiService == null) {
            throw new IllegalStateException("Reactive support is not enabled. Call withReactor() first.");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取图片字节数据
     */
    private byte[] getImageData() {
        if (imageData != null) {
            return imageData;
        }
        if (imageFile != null) {
            try {
                return Files.readAllBytes(imageFile.toPath());
            } catch (IOException e) {
                return null;
            }
        }
        if (imageStream != null) {
            try {
                return toByteArray(imageStream);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 将输入流转换为字节数组
     */
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
