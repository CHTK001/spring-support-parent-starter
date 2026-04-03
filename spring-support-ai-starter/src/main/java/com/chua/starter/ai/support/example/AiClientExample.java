package com.chua.starter.ai.support.example;

import com.chua.common.support.ai.AiClient;
import com.chua.common.support.ai.config.FaceConfig;
import com.chua.common.support.ai.config.OcrConfig;
import com.chua.common.support.ai.result.PredictResult;
import com.chua.common.support.ai.result.PredictResultObject;
import com.chua.deeplearning.support.config.LlmConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;

/**
 * AiClient 使用示例
 * 
 * @author CH
 * @since 2024-01-01
 */
@Component
public class AiClientExample {

    @Autowired
    private AiClient aiClient;

    /**
     * LLM 使用示例
     */
    public void llmExample() {
        // 使用默认配置创建 LLM 客户端
        var chatClient = aiClient.createLlm();
        var response = chatClient.chatSync("你好，请介绍一下你自己");
        System.out.println("LLM 响应: " + response);
        
        // 使用自定义配置创建 LLM 客户端
        var llmConfig = new LlmConfig();
        llmConfig.setProvider("openai");
        llmConfig.setModel("gpt-4");
        llmConfig.setTemperature(0.7);
        llmConfig.setMaxTokens(2000);
        
        var customChatClient = aiClient.createLlm(llmConfig);
        var customResponse = customChatClient.chatSync("请用简短的语言回答：什么是人工智能？");
        System.out.println("自定义 LLM 响应: " + customResponse);
    }

    /**
     * OCR 使用示例
     */
    public void ocrExample() throws Exception {
        // 使用默认配置创建 OCR 客户端
        var ocrBuilder = aiClient.createOcr();
        
        // 读取图片文件
        var imageFile = new File("/path/to/image.jpg");
        var imageData = Files.readAllBytes(imageFile.toPath());
        
        // 识别文字
        var text = ocrBuilder.recognize(imageData);
        System.out.println("识别文字: " + text);
        
        // 获取详细结果
        var detailResult = ocrBuilder.recognizeDetail(imageData);
        System.out.println("详细结果: " + detailResult);
        
        // 使用自定义配置创建 OCR 客户端
        var ocrConfig = new OcrConfig();
        ocrConfig.setProvider("baidu");
        ocrConfig.setLanguage("chi_sim");
        
        var customOcrBuilder = aiClient.createOcr(ocrConfig);
        var customText = customOcrBuilder.recognize(imageData);
        System.out.println("自定义 OCR 识别: " + customText);
    }

    /**
     * 人脸识别使用示例
     */
    public void faceExample() throws Exception {
        // 使用默认配置创建人脸识别客户端
        var faceBuilder = aiClient.createFace();
        
        // 读取图片文件
        var imageFile = new File("/path/to/face.jpg");
        var imageData = Files.readAllBytes(imageFile.toPath());
        
        // 人脸检测
        var detectResult = (PredictResultObject<PredictResult>) faceBuilder.detect(imageData);
        System.out.println("检测到人脸数量: " + detectResult.getList().size());
        
        // 人脸识别（提取特征）
        var recognizeResult = faceBuilder.recognize(imageData);
        System.out.println("人脸特征: " + recognizeResult);
        
        // 人脸比对
        var imageFile2 = new File("/path/to/face2.jpg");
        var imageData2 = Files.readAllBytes(imageFile2.toPath());
        var similarity = faceBuilder.compare(imageData, imageData2);
        System.out.println("人脸相似度: " + similarity);
        
        // 使用自定义配置创建人脸识别客户端
        var faceConfig = new FaceConfig();
        faceConfig.setProvider("aliyun");
        faceConfig.setConfidenceThreshold(0.5f);
        faceConfig.setNmsThreshold(0.4f);
        
        var customFaceBuilder = aiClient.createFace(faceConfig);
        var customDetectResult = customFaceBuilder.detect(imageData);
        System.out.println("自定义人脸检测: " + customDetectResult);
    }

    /**
     * 综合使用示例
     */
    public void comprehensiveExample() throws Exception {
        // 1. LLM 对话
        var chatClient = aiClient.createLlm();
        var llmResponse = chatClient.chatSync("请分析这张图片中的内容");
        
        // 2. OCR 识别
        var ocrBuilder = aiClient.createOcr();
        var imageData = Files.readAllBytes(new File("/path/to/document.jpg").toPath());
        var ocrText = ocrBuilder.recognize(imageData);
        
        // 3. 人脸检测
        var faceBuilder = aiClient.createFace();
        var faceImageData = Files.readAllBytes(new File("/path/to/face.jpg").toPath());
        var faceResult = (PredictResultObject<PredictResult>) faceBuilder.detect(faceImageData);
        
        // 4. 综合分析
        System.out.println("LLM 分析: " + llmResponse);
        System.out.println("OCR 识别: " + ocrText);
        System.out.println("人脸检测: " + faceResult.getList().size() + " 张人脸");
    }
}
