package com.chua.starter.ai.support.service.impl;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.image.ImageClassifier;
import com.chua.deeplearning.support.ocr.OcrPipeline;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.face.FaceClientManager;
import com.chua.starter.ai.support.configuration.ocr.OcrClientManager;
import com.chua.starter.ai.support.service.GlobalModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局模型服务实现。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalModelServiceImpl implements GlobalModelService {

    /**
    * 全局设置读取工具
    */
    private final GlobalSettingReader settingReader;

    /**
    * OCR 客户端管理器
    */
    private final OcrClientManager ocrClientManager;

    /**
    * 人脸客户端管理器
    */
    private final FaceClientManager faceClientManager;

    /**
    * 全局对话客户端
    */
    private volatile ChatClient chatClient;

    @Override
    public ChatClient getChatClient() {
        // 从 runtime 分组读取配置，创建全局 ChatClient
        // TODO: 完整实现，目前返回 null，由 Spring 容器中的 ChatClient 提供
        return chatClient;
    }

    @Override
    public OcrPipeline getOcrPipeline() {
        return ocrClientManager.current();
    }

    @Override
    public FacePipeline getFacePipeline() {
        return faceClientManager.currentPipeline();
    }

    @Override
    public ImageClassifier getImageClassifier() {
        // TODO: 从 image 分组读取配置，创建图片分类器
        return null;
    }

    @Override
    public String getConfig(String group, String field) {
        return settingReader.getStr(group, field);
    }

    @Override
    public String getConfig(String group, String field, String defaultValue) {
        String value = settingReader.getStr(group, field);
        return value != null ? value : defaultValue;
    }

    @Override
    public boolean getBoolConfig(String group, String field, boolean defaultValue) {
        return settingReader.getBool(group, field, defaultValue);
    }

    @Override
    public int getIntConfig(String group, String field, int defaultValue) {
        return settingReader.getInt(group, field, defaultValue);
    }

    @Override
    public Map<String, Map<String, String>> getConfigSummary() {
        Map<String, Map<String, String>> summary = new HashMap<>();

        // runtime 分组
        Map<String, String> runtime = new HashMap<>();
        runtime.put("defaultModel", getConfig("runtime", "default_model", "未配置"));
        summary.put("runtime", runtime);

        // ocr 分组
        Map<String, String> ocr = new HashMap<>();
        ocr.put("detectorModel", getConfig("ocr", "detector_model", "未配置"));
        ocr.put("recognizerModel", getConfig("ocr", "recognizer_model", "未配置"));
        summary.put("ocr", ocr);

        // face 分组
        Map<String, String> face = new HashMap<>();
        face.put("detectorModel", getConfig("face", "detector_model", "未配置"));
        face.put("featureModel", getConfig("face", "feature_model", "未配置"));
        summary.put("face", face);

        // image 分组
        Map<String, String> image = new HashMap<>();
        image.put("classifierModel", getConfig("image", "classifier_model", "未配置"));
        summary.put("image", image);

        return summary;
    }
}
