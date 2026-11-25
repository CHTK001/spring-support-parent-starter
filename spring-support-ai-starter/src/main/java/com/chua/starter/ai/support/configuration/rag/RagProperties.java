package com.chua.starter.ai.support.configuration.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 配置属性，前缀 {@code plugin.ai.rag}。
 *
 * <p>知识库（RAG）管线的引擎、向量存储、切片与召回默认值。完整 YAML 示例
 * （各键含义与默认值见字段注释）：</p>
 *
 * <pre>
 * plugin:
 *   ai:
 *     rag:
 *       enabled: true                       # 是否启用 RAG，默认 true
 *       embedding-engine: memory            # 向量化引擎，默认 memory
 *       vector-storage: jvector             # 向量存储，默认 jvector（缺 native 依赖自动降级内存）
 *       embedding-dimensions: 1536          # 向量维度，默认 1536
 *       chunk-size: 1000                    # 文本切片大小（字符），默认 1000
 *       chunk-overlap: 200                  # 文本切片重叠（字符），默认 200
 *       default-top-k: 5                    # 默认召回 Top-K，默认 5
 *       default-similarity-threshold: 0.7   # 默认相似度阈值，默认 0.7
 *       upload-dir: ./rag-uploads           # 上传目录，默认 ./rag-uploads
 * </pre>
 *
 * <p>运行期实际生效值还会被设置分组 {@code rag} 的用户覆盖 / 部署覆盖
 * （{@code plugin.ai.settings.rag.*}）进一步接管，本类只提供代码级默认。</p>
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
     * 是否启用 RAG。
     *
     * <p>默认 {@code true}；YAML：{@code plugin.ai.rag.enabled}。</p>
     */
    private boolean enabled = true;

    /**
     * 默认向量化引擎（内存 / openai / siliconflow 等）。
     *
     * <p>默认 {@code memory}；YAML：{@code plugin.ai.rag.embedding-engine}。</p>
     */
    private String embeddingEngine = "memory";

    /**
     * 默认向量数据库（jvector / milvus / MySQL / 内存）。
     *
     * <p>默认 {@code jvector}；YAML：{@code plugin.ai.rag.vector-storage}。
     * 当运行环境缺少 jvector 核心依赖（native VectorizationProvider）时，
     * {@link com.chua.starter.ai.support.configuration.rag.RagClientManager} 会自动降级为内存存储。</p>
     */
    private String vectorStorage = "jvector";

    /**
     * 向量维度（默认与 内存嵌入客户端 输出维度一致）。
     *
     * <p>默认 {@code 1536}；YAML：{@code plugin.ai.rag.embedding-dimensions}。</p>
     */
    // [P3C A20 豁免]
    private int embeddingDimensions = 1536;

    /**
     * 默认文本切片大小（字符）。
     *
     * <p>默认 {@code 1000}；YAML：{@code plugin.ai.rag.chunk-size}。</p>
     */
    // [P3C A20 豁免]
    private int chunkSize = 1000;

    /**
     * 默认文本切片重叠（字符）。
     *
     * <p>默认 {@code 200}；YAML：{@code plugin.ai.rag.chunk-overlap}。</p>
     */
    // [P3C A20 豁免]
    private int chunkOverlap = 200;

    /**
     * 默认召回 Top-K。
     *
     * <p>默认 {@code 5}；YAML：{@code plugin.ai.rag.default-top-k}。</p>
     */
    private int defaultTopK = 5;

    /**
     * 默认相似度阈值。
     *
     * <p>默认 {@code 0.7}；YAML：{@code plugin.ai.rag.default-similarity-threshold}。</p>
     */
    private double defaultSimilarityThreshold = 0.7;

    /**
     * 默认上传目录。
     *
     * <p>默认 {@code ./rag-uploads}；YAML：{@code plugin.ai.rag.upload-dir}。</p>
     */
    private String uploadDir = "./rag-uploads";
}
