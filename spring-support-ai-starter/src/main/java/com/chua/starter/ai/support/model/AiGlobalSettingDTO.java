package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 全局设置 DTO，字段命名与前端面板对齐。
 *
 * <p>前端提交的字段为 {@code chatClientEnabled}、{@code usageReportInterval} 等，
 * 与实体 {@code settingXxx} 前缀不同，故通过 DTO 隔离。</p>
 *
 * @since 2026-08-03
 * @author CH
 */
@Data
@Schema(name = "全局设置 DTO")
public class AiGlobalSettingDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 是否启用 ChatClient 聚合
     */
    @Schema(description = "是否启用 ChatClient 聚合")
    /** Chat客户端是否启用 */
    private Boolean chatClientEnabled;

    /**
     * 是否启用用量分析
     */
    @Schema(description = "是否启用用量分析")
    /** Enableusageanalysis */
    private Boolean enableUsageAnalysis;

    /**
     * 用量上报间隔（秒）
     */
    @Schema(description = "用量上报间隔(秒)")
    /** Usagereport间隔 */
    private Integer usageReportInterval;

    /**
     * 默认模型
     */
    @Schema(description = "默认模型")
    /** 默认模型 */
    private String defaultModel;

    /**
     * 默认温度
     */
    @Schema(description = "默认温度")
    /** Temperature */
    private BigDecimal temperature;

    /**
     * 默认最大 tokens
     */
    @Schema(description = "默认最大 tokens")
    /** 最大值tokens */
    private Integer maxTokens;

    /**
     * 默认请求超时（秒）
     */
    @Schema(description = "默认请求超时(秒)")
    /** 超时 */
    private Integer timeout;

    /**
     * 是否启用缓存
     */
    @Schema(description = "是否启用缓存")
    /** Enable缓存 */
    private Boolean enableCache;

    /**
     * 是否流式输出
     */
    @Schema(description = "是否流式输出")
    /** 流 */
    private Boolean stream;

    /**
     * 全局 ChatClient 默认系统提示词
     */
    @Schema(description = "全局 ChatClient 默认系统提示词")
    /** Systemprompt */
    private String systemPrompt;

    /**
     * RAG 向量模型
     */
    @Schema(description = "RAG 向量模型")
    /** RAGembedding模型 */
    private String ragEmbeddingModel;

    /**
     * RAG 问答 LLM 模型
     */
    @Schema(description = "RAG 问答 LLM 模型")
    /** RAGLLM模型 */
    private String ragLlmModel;

    /**
     * RAG OCR 识别模型
     */
    @Schema(description = "RAG OCR 识别模型")
    /** RAGOCR模型 */
    private String ragOcrModel;

    /**
     * RAG 上传文件目录
     */
    @Schema(description = "RAG 上传文件目录")
    /** RAG上传目录 */
    private String ragUploadDir;

    /**
     * RAG 向量数据库
     */
    @Schema(description = "RAG 向量数据库")
    /** RAGvector存储 */
    private String ragVectorStorage;

    /**
     * RAG 问答系统提示词
     */
    @Schema(description = "RAG 问答系统提示词")
    /** RAGsystemprompt */
    private String ragSystemPrompt;

    /**
     * RAG 文本切片大小（字符）
     */
    @Schema(description = "RAG 文本切片大小(字符)")
    /** RAGchunk尺寸 */
    private Integer ragChunkSize;

    /**
     * RAG 文本切片重叠（字符）
     */
    @Schema(description = "RAG 文本切片重叠(字符)")
    /** RAGchunkoverlap */
    private Integer ragChunkOverlap;

    /**
     * RAG 文本切片策略（TextSplitter SPI 名称，默认 sentence）
     */
    @Schema(description = "RAG 文本切片策略")
    /** RAGsplitter */
    private String ragSplitter;
}