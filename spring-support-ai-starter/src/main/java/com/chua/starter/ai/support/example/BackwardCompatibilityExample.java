package com.chua.starter.ai.support.example;

import com.chua.starter.ai.support.chat.AiChat;
import com.chua.starter.ai.support.model.FaceDetectionResult;
import com.chua.starter.ai.support.model.FeatureResult;
import com.chua.starter.ai.support.model.OcrResult;
import com.chua.starter.ai.support.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;

/**
 * 向后兼容 API 使用示例
 * 
 * 展示如何使用现有的 AiService 和 AiChat API
 * 
 * @author CH
 * @since 2024-01-01
 */
@Component
public class BackwardCompatibilityExample {

    @Autowired
    private AiService aiService;

    /**
     * 使用 AiService 接口示例
     */
    public void aiServiceExample() throws Exception {
        // 读取图片文件
        var imageFile = new File("/path/to/image.jpg");
        var imageData = Files.readAllBytes(imageFile.toPath());
        
        // 1. 人脸检测
        FaceDetectionResult faceResult = aiService.detectFaces(imageData);
        if (faceResult.isSuccess()) {
            System.out.println("检测到 " + faceResult.getFaces().size() + " 张人脸");
            faceResult.getFaces().forEach(face -> {
                System.out.println("人脸位置: (" + face.getX() + ", " + face.getY() + ")");
                System.out.println("置信度: " + face.getConfidence());
            });
        }
        
        // 2. 人脸特征提取
        FeatureResult faceFeature = aiService.extractFaceFeature(imageData);
        if (faceFeature.isSuccess()) {
            System.out.println("人脸特征维度: " + faceFeature.getFeature().length);
        }
        
        // 3. OCR 识别
        OcrResult ocrResult = aiService.ocr(imageData);
        if (ocrResult.isSuccess()) {
            System.out.println("识别文字: " + ocrResult.getFullText());
            ocrResult.getTextLines().forEach(line -> {
                System.out.println("文本行: " + line.getText() + " (置信度: " + line.getConfidence() + ")");
            });
        }
        
        // 4. 图像特征提取
        FeatureResult imageFeature = aiService.extractImageFeature(imageData);
        if (imageFeature.isSuccess()) {
            System.out.println("图像特征维度: " + imageFeature.getFeature().length);
        }
    }

    /**
     * 使用 AiChat 链式 API 示例
     */
    public void aiChatExample() throws Exception {
        // 读取图片文件
        var imageFile = new File("/path/to/image.jpg");
        var imageData = Files.readAllBytes(imageFile.toPath());
        
        // 1. 人脸检测（链式调用）
        FaceDetectionResult faceResult = AiChat.of(aiService)
                .image(imageData)
                .detectFaces();
        
        System.out.println("检测到 " + faceResult.getFaces().size() + " 张人脸");
        
        // 2. 人脸特征提取（链式调用）
        FeatureResult faceFeature = AiChat.of(aiService)
                .image(imageFile)
                .faceFeature();
        
        System.out.println("人脸特征维度: " + faceFeature.getFeature().length);
        
        // 3. OCR 识别（链式调用）
        OcrResult ocrResult = AiChat.of(aiService)
                .image(imageData)
                .ocr();
        
        System.out.println("识别文字: " + ocrResult.getFullText());
        
        // 4. 图像特征提取（链式调用）
        FeatureResult imageFeature = AiChat.of(aiService)
                .image(imageFile)
                .imageFeature();
        
        System.out.println("图像特征维度: " + imageFeature.getFeature().length);
        
        // 5. 文本特征提取（链式调用）
        FeatureResult textFeature = AiChat.of(aiService)
                .text("这是一段测试文本")
                .textFeature();
        
        System.out.println("文本特征维度: " + textFeature.getFeature().length);
    }

    /**
     * 异步调用示例
     */
    public void asyncExample() throws Exception {
        var imageFile = new File("/path/to/image.jpg");
        var imageData = Files.readAllBytes(imageFile.toPath());
        
        // 异步人脸检测
        AiChat.of(aiService)
                .image(imageData)
                .detectFacesAsync()
                .thenAccept(result -> {
                    System.out.println("异步检测到 " + result.getFaces().size() + " 张人脸");
                })
                .exceptionally(ex -> {
                    System.err.println("异步人脸检测失败: " + ex.getMessage());
                    return null;
                });
        
        // 异步 OCR 识别
        AiChat.of(aiService)
                .image(imageData)
                .ocrAsync()
                .thenAccept(result -> {
                    System.out.println("异步识别文字: " + result.getFullText());
                })
                .exceptionally(ex -> {
                    System.err.println("异步 OCR 识别失败: " + ex.getMessage());
                    return null;
                });
    }

    /**
     * Reactor 响应式调用示例
     */
    public void reactiveExample() throws Exception {
        var imageFile = new File("/path/to/image.jpg");
        var imageData = Files.readAllBytes(imageFile.toPath());
        
        // 响应式人脸检测
        AiChat.of(aiService)
                .withReactor()
                .image(imageData)
                .detectFacesMono()
                .subscribe(
                        result -> System.out.println("响应式检测到 " + result.getFaces().size() + " 张人脸"),
                        error -> System.err.println("响应式人脸检测失败: " + error.getMessage())
                );
        
        // 响应式 OCR 识别
        AiChat.of(aiService)
                .withReactor()
                .image(imageData)
                .ocrMono()
                .subscribe(
                        result -> System.out.println("响应式识别文字: " + result.getFullText()),
                        error -> System.err.println("响应式 OCR 识别失败: " + error.getMessage())
                );
    }
}
