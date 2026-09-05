package com.chua.starter.ai.support.configuration.image;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 图片识别模块全局设置提供者 SPI。
 *
 * <p>定义 {@code image} 分组的全部配置项，由
 * {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。</p>
 *
 * <p>图片识别管线：classify(图像分类) → detect(目标检测) → segment(图像分割) →
 * captioning(图像描述)；辅助能力：depth(深度估计) / pedestrian(行人) / quality(图像质量)。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("image")
public class ImageRecognitionGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "image";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "图片识别";

    @Override
    /** 分组 */
    public String group() {
        return GROUP;
    }

    @Override
    /** 分组Name */
    public String groupName() {
        return GROUP_NAME;
    }

    @Override
    /** 获取Items */
    public List<GlobalSettingProvider.SettingItem> getItems() {
        return List.of(
                new GlobalSettingProvider.SettingItem("classify_model", "", "STRING", "图像分类模型（如 google-vit / mobilenet-classify，留空关闭）", 1),
                new GlobalSettingProvider.SettingItem("classify_threshold", "0.5", "NUMBER", "图像分类置信度阈值（0~1）", 2),
                new GlobalSettingProvider.SettingItem("detect_model", "", "STRING", "目标检测模型（如 yolo-v8-detect / rtdetr，留空关闭）", 3),
                new GlobalSettingProvider.SettingItem("detect_threshold", "0.5", "NUMBER", "目标检测置信度阈值（0~1）", 4),
                new GlobalSettingProvider.SettingItem("segment_model", "", "STRING", "图像分割模型（如 sam-vit-base / yolo-seg，留空关闭）", 5),
                new GlobalSettingProvider.SettingItem("captioning_model", "", "STRING", "图像描述模型（如 blip2 / git-base，留空关闭）", 6),
                new GlobalSettingProvider.SettingItem("depth_enabled", "false", "BOOLEAN", "是否启用深度估计", 7),
                new GlobalSettingProvider.SettingItem("pedestrian_enabled", "false", "BOOLEAN", "是否启用行人与异常检测", 8),
                new GlobalSettingProvider.SettingItem("quality_enabled", "false", "BOOLEAN", "是否启用图像质量评估", 9),
                new GlobalSettingProvider.SettingItem("device", "cpu", "STRING", "推理设备（cpu / cuda）", 10)
        );
    }
}