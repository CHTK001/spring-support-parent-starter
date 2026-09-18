package com.chua.starter.ai.support.service;

import com.chua.common.support.ai.chat.ChatClient;
import com.chua.deeplearning.support.face.FacePipeline;
import com.chua.deeplearning.support.image.ImageClassifier;
import com.chua.deeplearning.support.ocr.OcrPipeline;

import java.util.Map;

/**
 * 全局模型服务接口。
 *
 * <p>提供各个默认模型分组对应的全局 Bean，方便其它模块直接注入使用。
 * 所有 Bean 都支持热更新，配置变更后自动重建。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface GlobalModelService {

    /**
    * 获取全局对话客户端（runtime 分组配置）。
    *
    * @return 对话客户端
    */
    ChatClient getChatClient();

    /**
    * 获取 OCR 识别管线（ocr 分组配置）。
    *
    * @return OCR 管线
    */
    OcrPipeline getOcrPipeline();

    /**
    * 获取人脸识别管线（face 分组配置）。
    *
    * @return 人脸管线
    */
    FacePipeline getFacePipeline();

    /**
    * 获取图片分类器（image 分组配置）。
    *
    * @return 图片分类器
    */
    ImageClassifier getImageClassifier();

    /**
    * 获取配置值。
    *
    * @param group 分组
    * @param field 字段名
    * @return 配置值
    */
    String getConfig(String group, String field);

    /**
    * 获取配置值，带默认值。
    *
    * @param group 分组
    * @param field 字段名
    * @param defaultValue 默认值
    * @return 配置值
    */
    String getConfig(String group, String field, String defaultValue);

    /**
    * 获取布尔类型配置值。
    *
    * @param group 分组
    * @param field 字段名
    * @param defaultValue 默认值
    * @return 配置值
    */
    boolean getBoolConfig(String group, String field, boolean defaultValue);

    /**
    * 获取整数类型配置值。
    *
    * @param group 分组
    * @param field 字段名
    * @param defaultValue 默认值
    * @return 配置值
    */
    int getIntConfig(String group, String field, int defaultValue);

    /**
    * 获取所有分组的配置摘要。
    *
    * @return 分组 → 配置摘要
    */
    Map<String, Map<String, String>> getConfigSummary();
}
