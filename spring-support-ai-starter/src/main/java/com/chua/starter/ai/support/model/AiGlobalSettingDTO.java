package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

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
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 是否启用 对话客户端 聚合
     */
    @Schema(description = "是否启用 ChatClient 聚合")
    /**
     * 对话客户端是否启用
    */
    private Boolean chatClientEnabled;

    /**
     * 是否启用用量分析
     */
    @Schema(description = "是否启用用量分析")
    /**
     * Enableusageanalysis
    */
    private Boolean enableUsageAnalysis;

    /**
     * 用量上报间隔（秒）
     */
    @Schema(description = "用量上报间隔(秒)")
    /**
     * Usagereport间隔
    */
    private Integer usageReportInterval;

    /**
     * 默认模型
     */
    @Schema(description = "默认模型")
    /**
     * 默认模型
    */
    private String defaultModel;

    /**
     * 默认温度
     */
    @Schema(description = "默认温度")
    /**
     * Temperature
    */
    private BigDecimal temperature;

    /**
     * 默认最大 令牌
     */
    @Schema(description = "默认最大 tokens")
    /**
     * 最大值令牌
    */
    private Integer maxTokens;

    /**
     * 默认请求超时（秒）
     */
    @Schema(description = "默认请求超时(秒)")
    /**
     * 超时
    */
    private Integer timeout;

    /**
     * 全局 对话客户端 默认系统提示词
     */
    @Schema(description = "全局 ChatClient 默认系统提示词")
    /**
     * Systemprompt
    */
    private String systemPrompt;

    /**
     * RAG 向量模型
     */
    @Schema(description = "RAG 向量模型")
    /**
     * ragembedding模型
    */
    private String ragEmbeddingModel;

    /**
     * RAG 问答 LLM 模型
     */
    @Schema(description = "RAG 问答 LLM 模型")
    /**
     * RAGLLM模型
    */
    private String ragLlmModel;

    /**
     * RAG 上传文件目录
     */
    @Schema(description = "RAG 上传文件目录")
    /**
     * RAG上传目录
    */
    private String ragUploadDir;

    /**
     * RAG 向量数据库
     */
    @Schema(description = "RAG 向量数据库")
    /**
     * ragvector存储
    */
    private String ragVectorStorage;

    /**
     * RAG 向量库配置：SPI 名称到该库键值配置的映射。
     *
     * <p>例如 {@code {"milvus":{"host":"...","port":"19530","collection":"rag","token":"..."}}}。
     * 按向量库分桶保存，来回切换向量库时各自的 host / token / 表名都保留。</p>
     *
     * <p>字段结构由 {@code /v2/rag/options} 返回的 vectorStorageOptions 描述，
     * 前端按描述渲染表单即可，新增向量库无需改前端。
     * 标记为敏感（sensitive）的字段保存后不再回显。</p>
     */
    @Schema(description = "RAG 向量库配置：SPI 名称 -> 键值配置")
    /**
     * rag向量存储配置
     */
    private Map<String, Map<String, Object>> ragVectorStorageConfig;

    /**
     * RAG 问答系统提示词
     */
    @Schema(description = "RAG 问答系统提示词")
    /**
     * ragsystemprompt
    */
    private String ragSystemPrompt;

    /**
     * RAG 文本切片大小（字符）
     */
    @Schema(description = "RAG 文本切片大小(字符)")
    /**
     * ragchunk尺寸
    */
    private Integer ragChunkSize;

    /**
     * RAG 文本切片重叠（字符）
     */
    @Schema(description = "RAG 文本切片重叠(字符)")
    /**
     * ragchunkoverlap
    */
    private Integer ragChunkOverlap;

    /**
     * RAG 文本切片策略，对应文本 splitter SPI 名称，默认使用 sentence。
     */
    @Schema(description = "RAG 文本切片策略")
    /**
     * RAG 文本切片策略。
     */
    private String ragSplitter;

    /**
     * 是否启用协议服务端，对应映射到 runtime.protocol_server_enabled。
     * 关闭时 {@code AiProtocolController} 的三个对外协议入口统一回「服务未启用」，
     * 且不再注册协议过滤器 Bean。
     */
    @Schema(description = "是否启用协议服务端")
    /**
     * 协议服务端是否启用。
     */
    private Boolean protocolServerEnabled;
}
