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
 * <p>完整 YAML 示例（33 个键均默认未配置，键名 = kebab-case 字段名，
 * 与库字段一一对应；此处列 7 个，其余同形）：</p>
 *
 * <pre>
 * plugin:
 *   ai:
 *     default-model:
 *       chat: zhipu:glm-4-flash              # 默认对话模型
 *       title-summary: zhipu:glm-4-flash     # 默认标题总结模型
 *       rag-embedding: bge-large-zh          # 默认 RAG 嵌入模型
 *       rerank: bge-reranker-base            # 默认重排序模型
 *       text-to-image: dall-e-3              # 默认文生图模型
 *       text-to-video: sora                  # 默认文生视频模型
 *       ocr-recognize: onnx:paddleocrv6-rec  # 默认 OCR 识别模型
 * </pre>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = "plugin.ai.default-model")
public class DefaultModelProperties {

    /**
     * 默认对话模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.chat}。</p>
     */
    private String chat;

    /**
     * 默认标题总结模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.title-summary}。</p>
     */
    private String titleSummary;

    /**
     * 默认视觉辅助模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.visual-assistant}。</p>
     */
    private String visualAssistant;

    /**
     * 默认历史压缩模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.history-compression}。</p>
     */
    private String historyCompression;

    /**
     * 默认历史压缩提示词（与历史压缩模型同组，供外部模块一并读取）。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.history-compression-prompt}。</p>
     */
    private String historyCompressionPrompt;

    /**
     * 默认翻译模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.translation}。</p>
     */
    private String translation;

    /**
     * 默认翻译提示词（与翻译模型同组，供外部模块一并读取）。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.translation-prompt}。</p>
     */
    private String translationPrompt;

    /**
     * 默认车牌检测模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.plate-detect}。</p>
     */
    private String plateDetect;

    /**
     * 默认车牌识别模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.plate-recognize}。</p>
     */
    private String plateRecognize;

    /**
     * 默认特征提取模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.feature}。</p>
     */
    private String feature;

    /**
     * 默认 RAG 嵌入模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.rag-embedding}。</p>
     */
    private String ragEmbedding;

    /**
     * 默认重排序模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.rerank}。</p>
     */
    private String rerank;

    /**
     * 默认图像质量评估模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.image-quality}。</p>
     */
    private String imageQuality;

    /**
     * 默认文生视频模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.text-to-video}。</p>
     */
    private String textToVideo;

    /**
     * 默认文生图模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.text-to-image}。</p>
     */
    private String textToImage;

    /**
     * 默认图像增强模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.image-enhance}。</p>
     */
    private String imageEnhance;

    /**
     * 默认 OCR 文字检测模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.ocr-detect}。</p>
     */
    private String ocrDetect;

    /**
     * 默认 OCR 文字识别模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.ocr-recognize}。</p>
     */
    private String ocrRecognize;

    /**
     * 默认 OCR 方向矫正模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.ocr-direction}。</p>
     */
    private String ocrDirection;

    /**
     * 默认 OCR 超分模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.ocr-super-resolution}。</p>
     */
    private String ocrSuperResolution;

    /**
     * 默认版面分析模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.layout}。</p>
     */
    private String layout;

    /**
     * 默认人脸检测模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.face-detect}。</p>
     */
    private String faceDetect;

    /**
     * 默认人脸识别（特征）模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.face-recognize}。</p>
     */
    private String faceRecognize;

    /**
     * 默认活体检测模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.liveness}。</p>
     */
    private String liveness;

    /**
     * 默认眼睛检测模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.eye-detect}。</p>
     */
    private String eyeDetect;

    /**
     * 默认微笑检测模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.smile-detect}。</p>
     */
    private String smileDetect;

    /**
     * 默认图像分类模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.image-classify}。</p>
     */
    private String imageClassify;

    /**
     * 默认目标检测模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.image-detect}。</p>
     */
    private String imageDetect;

    /**
     * 默认图像分割模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.image-segment}。</p>
     */
    private String imageSegment;

    /**
     * 默认图像描述模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.image-captioning}。</p>
     */
    private String imageCaptioning;

    /**
     * 默认深度估计模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.depth}。</p>
     */
    private String depth;

    /**
     * 默认人脸质量评估模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.face-quality}。</p>
     */
    private String faceQuality;

    /**
     * 默认行人检测模型。
     *
     * <p>默认未配置（{@code null}，此时仅由页面/数据库决定）；YAML：{@code plugin.ai.default-model.pedestrian}。</p>
     */
    private String pedestrian;
}
