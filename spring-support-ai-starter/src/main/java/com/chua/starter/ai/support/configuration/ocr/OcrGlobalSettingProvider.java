// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
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
 * 文字超分修复、correctorModel 文本矫正（如 macbert4csc，留空关闭、失败保留原文，非必填）。
 * 检测置信度阈值过滤低分检测框，避免误识别背景噪声。</p>
 *
 * @since 4.0.0.42
 * @author CH
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
    /**
     * 分组
     * @return 返回值
    */
    public String group() {
        return GROUP;
    }

    @Override
    /**
     * 分组名称
     * @return 返回值
    */
    public String groupName() {
        return GROUP_NAME;
    }

    @Override
    /**
     * 获取Items
     * @return 返回值
    */
    public List<GlobalSettingProvider.SettingItem> getItems() {
        return List.of(
                // 默认留空：ONNX 未配置时这些模型并不存在，写死模型名会在首次调用时直接报错；
                // 空值由 OcrClientManager.build() 判定为「OCR 未启用」，语义更准确
                new GlobalSettingProvider.SettingItem("detector_model", "", "STRING", "文字检测模型", 1),
                new GlobalSettingProvider.SettingItem("recognizer_model", "", "STRING", "文字识别模型", 2),
                new GlobalSettingProvider.SettingItem("detector_threshold", "0.5", "NUMBER", "OCR 检测置信度阈值（过滤低置信度文本框，0~1）", 3),
                new GlobalSettingProvider.SettingItem("sort_reading_order", "true", "BOOLEAN", "是否按阅读顺序排序（先 y 后 x）", 4),
                new GlobalSettingProvider.SettingItem("direction_model", "", "STRING", "方向矫正模型（留空关闭）", 5),
                new GlobalSettingProvider.SettingItem("restorer_model", "", "STRING", "文字超分修复模型（留空关闭）", 6),
                new GlobalSettingProvider.SettingItem("corrector_model", "", "STRING", "文本矫正模型（留空关闭，如 macbert4csc；非必填，矫正失败保留原文）", 7),
                new GlobalSettingProvider.SettingItem("device", "cpu", "STRING", "推理设备（cpu / cuda）", 8),
                // ── 可选增强（全部非必填：留空/关闭时原图直通，不影响识别）──
                new GlobalSettingProvider.SettingItem("ocr_max_image_size", "2048", "NUMBER",
                        "长边上限（像素，超过则等比缩小；0=不缩放）", 9),
                new GlobalSettingProvider.SettingItem("enhance_deskew", "true", "BOOLEAN",
                        "自动纠偏（±8° 内估角并旋转；角度过小不动图）", 10),
                new GlobalSettingProvider.SettingItem("enhance_contrast", "false", "BOOLEAN",
                        "对比度拉伸（适合偏灰照片；干净扫描件建议关）", 11),
                new GlobalSettingProvider.SettingItem("enhance_denoise", "false", "BOOLEAN",
                        "中值去噪（适合噪点照片；干净扫描件建议关）", 12),
                new GlobalSettingProvider.SettingItem("enhance_binarize", "false", "BOOLEAN",
                        "Otsu 二值化（默认关：对浅色/彩色卷面有害）", 13),
                new GlobalSettingProvider.SettingItem("ocr_confidence_threshold", "0", "NUMBER",
                        "结果置信度过滤阈值（0~1，0=不过滤）", 14)
        );
    }
}