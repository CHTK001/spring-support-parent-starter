package com.chua.starter.ai.support.properties;

import com.chua.common.support.ai.config.AgentProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 模块配置属性
 * <p>
 * 支持多云厂商配置和统一的 AI 能力管理。
 *
 * @author CH
 * @since 2025/01/XX
 */
@Data
@ConfigurationProperties(prefix = AiProperties.PREFIX)
public class AiProperties {

    public static final String PREFIX = "spring.ai";

    /**
     * 是否启用 AI 模块
     */
    private boolean enabled = true;

    /**
     * 默认提供商
     */
    private String defaultProvider = "siliconflow";

    /**
     * LLM 配置
     */
    private LlmProperties llm = new LlmProperties();

    /**
     * 额外 LLM 路由配置。
     * <p>
     * 旧版运行时路由配置，保留仅用于兼容。
     */
    @Deprecated
    private Map<String, LlmProperties> llms = new LinkedHashMap<>();

    /**
     * 启动时默认激活的 LLM 路由。
     */
    @Deprecated
    private String activeLlm = "default";

    /**
     * Agent 配置。
     */
    private AgentProperties agent = new AgentProperties();

    /**
     * OCR 配置
     */
    private OcrProperties ocr = new OcrProperties();

    /**
     * 人脸识别配置
     */
    private FaceProperties face = new FaceProperties();

    /**
     * 网站性能分析配置
     */
    private AiPerformanceProperties performance = new AiPerformanceProperties();

    /**
     * 云厂商配置
     * <p>
     * key: 提供商名称（如 openai, aliyun, baidu, tencent）
     * value: 提供商配置
     */
    private Map<String, ProviderProperties> providers = new HashMap<>();

    // ========== 向后兼容：保留旧的配置结构 ==========

    /**
     * 模型存放路径（向后兼容）
     * @deprecated 使用 providers[xxx].modelPath 替代
     */
    @Deprecated
    private String modelPath;

    /**
     * 人脸检测配置（向后兼容）
     * @deprecated 使用 face 替代
     */
    @Deprecated
    private AiLegacyFaceDetectionProperties faceDetection = new AiLegacyFaceDetectionProperties();

    /**
     * 图像配置（向后兼容）
     */
    private AiLegacyImageProperties image = new AiLegacyImageProperties();

    /**
     * 文本配置（向后兼容）
     */
    private AiLegacyTextProperties text = new AiLegacyTextProperties();
}
