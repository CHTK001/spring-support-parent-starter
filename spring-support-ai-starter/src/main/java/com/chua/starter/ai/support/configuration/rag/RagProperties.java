package com.chua.starter.ai.support.configuration.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 配置属性，前缀 {@code plugin.ai.rag}。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = RagProperties.PRE)
public class RagProperties {

    /**
     * 配置前缀
     */
    public static final String PRE = "plugin.ai.rag";

    /**
     * 是否启用 RAG
     */
    private boolean enabled = true;

    /**
     * 默认向量化引擎（内存 / openai / siliconflow 等）
     */
    private String embeddingEngine = "memory";

/**
 * 默认向量数据库（jvector / milvus / MySQL / 内存）
 *
 * <p>默认 {@code jvector}；当运行环境缺少 jvector 核心依赖（native VectorizationProvider）时，
 * {@link com.chua.starter.ai.support.configuration.rag.RagClientManager} 会自动降级为内存存储。</p>
 */
    private String vectorStorage = "jvector";

    /**
     * 向量维度（默认与 内存嵌入客户端 输出维度一致）
     */
    private int embeddingDimensions = 1536;

    /**
     * 默认文本切片大小（字符）
     */
    private int chunkSize = 1000;

    /**
     * 默认文本切片重叠（字符）
     */
    private int chunkOverlap = 200;

    /**
     * 默认召回 Top-K
     */
    private int defaultTopK = 5;

    /**
     * 默认相似度阈值
     */
    private double defaultSimilarityThreshold = 0.7;

    /**
     * 默认上传目录
     */
    private String uploadDir = "./rag-uploads";
}
