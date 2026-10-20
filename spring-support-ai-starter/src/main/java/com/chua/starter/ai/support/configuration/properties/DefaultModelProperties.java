package com.chua.starter.ai.support.configuration.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 默认模型配置属性（yaml 侧）。
 *
 * <p>对应页面「系统配置 → 默认模型」写入的 {@code sys_ai_global_setting} 表
 * 中 {@code setting_group = 'models'} 分组的全部字段。字段名与库中字段一一对应，
 * 因此 yaml 配置项可平铺理解为"页面配置的代码默认值"。</p>
 *
 * <p><b>优先级</b>：页面配置（DB）&gt; yaml（本类）。即：
 * <ul>
 *   <li>DB 中该字段有非空值 → 用 DB（页面配置优先）</li>
 *   <li>DB 中为空/未配置 → 用 yaml 兜底</li>
 * </ul>
 * 合并逻辑见 {@link DefaultModelResolver}。</p>
 *
 * <p><b>用途</b>：其它模块依赖 ai-starter 但自身没有配置页面时，
 * 可通过 yaml 直接提供默认模型，并注入 {@link DefaultModelResolver}
 * 这个全局 Bean 读取最终生效值，无需访问数据库。</p>
 *
 * <p>取值格式与页面一致：{@code 纯模型名} 或 {@code providerId:modelName}
 * （后者用于区分同名模型，解析时两者都能取到）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.ai.default-model")
public class DefaultModelProperties {

    /**
     * 默认对话模型
     */
    private String chat;

    /**
     * 默认标题总结模型
     */
    private String titleSummary;

    /**
     * 默认视觉辅助模型
     */
    private String visualAssistant;

    /**
     * 默认历史压缩模型
     */
    private String historyCompression;

    /**
     * 默认历史压缩提示词（与历史压缩模型同组，供外部模块一并读取）
     */
    private String historyCompressionPrompt;

    /**
     * 默认翻译模型
     */
    private String translation;

    /**
     * 默认翻译提示词（与翻译模型同组，供外部模块一并读取）
     */
    private String translationPrompt;

    /**
     * 默认车牌检测模型
     */
    private String plateDetect;

    /**
     * 默认车牌识别模型
     */
    private String plateRecognize;

    /**
     * 默认特征提取模型
     */
    private String feature;

    /**
     * 默认 RAG 嵌入模型
     */
    private String ragEmbedding;

    /**
     * 默认重排序模型
     */
    private String rerank;

    /**
     * 默认图像质量评估模型
     */
    private String imageQuality;

    /**
     * 默认文生视频模型
     */
    private String textToVideo;

    /**
     * 默认文生图模型
     */
    private String textToImage;

    /**
     * 默认图像增强模型
     */
    private String imageEnhance;

    /**
     * 默认 OCR 文字检测模型
     */
    private String ocrDetect;

    /**
     * 默认 OCR 文字识别模型
     */
    private String ocrRecognize;

    /**
     * 默认 OCR 方向矫正模型
     */
    private String ocrDirection;

    /**
     * 默认 OCR 超分模型
     */
    private String ocrSuperResolution;

    /**
     * 默认版面分析模型
     */
    private String layout;

    /**
     * 默认人脸检测模型
     */
    private String faceDetect;

    /**
     * 默认人脸识别（特征）模型
     */
    private String faceRecognize;

    /**
     * 默认活体检测模型
     */
    private String liveness;

    /**
     * 默认眼睛检测模型
     */
    private String eyeDetect;

    /**
     * 默认微笑检测模型
     */
    private String smileDetect;

    /**
     * 默认图像分类模型
     */
    private String imageClassify;

    /**
     * 默认目标检测模型
     */
    private String imageDetect;

    /**
     * 默认图像分割模型
     */
    private String imageSegment;

    /**
     * 默认图像描述模型
     */
    private String imageCaptioning;

    /**
     * 默认深度估计模型
     */
    private String depth;

    /**
     * 默认人脸质量评估模型
     */
    private String faceQuality;

    /**
     * 默认行人检测模型
     */
    private String pedestrian;
}
