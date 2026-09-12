package com.chua.starter.ai.support.configuration.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
* RAG 配置属性，前缀 {@code spring.ai.rag}。
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
    public static final String PRE = "spring.ai.rag";

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
     */
    private String vectorStorage = "jvector";

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
