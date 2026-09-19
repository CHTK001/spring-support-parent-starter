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
     * 是否启用缓存
     */
    @Schema(description = "是否启用缓存")
    /**
     * Enable缓存
    */
    private Boolean enableCache;

    /**
     * 是否流式输出
     */
    @Schema(description = "是否流式输出")
    /**
     * 流
    */
    private Boolean stream;

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
     * RAG OCR 识别模型
     */
    @Schema(description = "RAG OCR 识别模型")
    /**
     * RAGOCR模型
    */
    private String ragOcrModel;

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
     * RAG �ı���Ƭ���ԣ�文本splitter SPI ���ƣ�Ĭ�� sentence��
     */
    @Schema(description = "RAG �ı���Ƭ����")
    /**
     * ragsplitter
    */
    private String ragSplitter;

    /**
     * ���⹤���ʲ��洢Ŀ¼��ӳ�䵽 workshop.storage_dir��
     */
    @Schema(description = "���⹤���ʲ��洢Ŀ¼")
    /**
     * Workshop存储目录
    */
    private String workshopStorageDir;

    /**
     * ���⹤�������ϴ���С���ޣ�MB����ӳ�䵽 workshop.最大_upload_大小_mb��
     */
    @Schema(description = "���⹤�������ϴ���С����(MB)")
    /**
     * �����ϴ���С����
    */
    private Integer workshopMaxUploadSizeMb;

    /**
     * ���⹤�������ϴ��ļ���չ����Ȩ�б���Ӣ�Ķ��ŷָ����� jpg,png,webp,mp4 ������ӳ�䵽 workshop.allowed_延伸��
     */
    @Schema(description = "���⹤�������ϴ��ļ���չ��(��ŷָ�, �� jpg,png,webp,mp4)")
    /**
     * �ϴ��ļ���չ��
    */
    private String workshopAllowedExtensions;

    /**
     * �Ƿ���������Э������ 打开AI客户端/Claude/Gemini ��Ӧ�˵㣩��ӳ�䵽 runtime.协议_服务端_已启用��
     */
    @Schema(description = "�Ƿ���������Э������")
    /**
     * Э��������Ƿ�����
    */
    private Boolean protocolServerEnabled;

    /**
     * ����Э������˿ڣ�ӳ�䵽 runtime.协议_服务端_端口��
     */
    @Schema(description = "����Э������˿�")
    /**
     * Э��������˿�
    */
    private Integer protocolServerPort;
}
