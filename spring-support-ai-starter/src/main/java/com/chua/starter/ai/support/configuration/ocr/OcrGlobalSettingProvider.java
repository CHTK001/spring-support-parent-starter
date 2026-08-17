package com.chua.starter.ai.support.configuration.ocr;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * OCR 模块全局设置提供者 SPI。
 *
 * <p>定义 {@code ocr} 分组的全部配置项，由
 * {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。</p>
 *
 * <p>OCR 核心链路：detect → crop → recognize；可选 directionModel 方向矫正、restorerModel
 * 文字超分修复。检测置信度阈值过滤低分检测框，避免误识别背景噪声。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi("ocr")
public class OcrGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "ocr";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "OCR 文字识别";

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    @Override
    public List<GlobalSettingProvider.SettingItem> getItems() {
        return List.of(
                new GlobalSettingProvider.SettingItem("detector_model", "paddleocrv6-det", "STRING", "文字检测模型（paddleocrv6-det / paddleocrv6-medium-det）", 1),
                new GlobalSettingProvider.SettingItem("recognizer_model", "paddleocrv6-rec", "STRING", "文字识别模型（paddleocrv6-rec / paddleocrv6-medium-rec / svtr-extractor / paddle-ocr-recognition / pp-word-extractor）", 2),
                new GlobalSettingProvider.SettingItem("detector_threshold", "0.5", "NUMBER", "OCR 检测置信度阈值（过滤低置信度文本框，0~1）", 3),
                new GlobalSettingProvider.SettingItem("sort_reading_order", "true", "BOOLEAN", "是否按阅读顺序排序（先 y 后 x）", 4),
                new GlobalSettingProvider.SettingItem("direction_model", "", "STRING", "方向矫正模型（pp-word-rotate，留空关闭）", 5),
                new GlobalSettingProvider.SettingItem("restorer_model", "", "STRING", "文字超分修复模型（text-bsr，留空关闭）", 6),
                new GlobalSettingProvider.SettingItem("device", "cpu", "STRING", "推理设备（cpu / cuda）", 7)
        );
    }
}
