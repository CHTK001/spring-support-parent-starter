package com.chua.starter.ai.support.entity.settings;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 全局设置表，单条记录（id=1），保存应用级默认参数。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@TableName("ai_global_setting")
@Schema(name = "全局设置")
public class AiGlobalSetting implements Serializable {

    /**
     * 设置主键 ID
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "设置主键 ID")
    private Long settingId;

    /**
     * 是否启用 ChatClient 聚合
     */
    @TableField("setting_chat_client_enabled")
    @Schema(description = "是否启用 ChatClient 聚合")
    private Boolean settingChatClientEnabled;

    /**
     * 用量自动刷新间隔（秒）
     */
    @TableField("setting_usage_interval_sec")
    @Schema(description = "用量自动刷新间隔(秒)")
    private Integer settingUsageIntervalSec;

    /**
     * 是否启用用量分析
     */
    @TableField("setting_enable_usage_analysis")
    @Schema(description = "是否启用用量分析")
    private Boolean settingEnableUsageAnalysis;

    /**
     * 默认模型
     */
    @TableField("setting_default_model")
    @Schema(description = "默认模型")
    private String settingDefaultModel;

    /**
     * 默认温度
     */
    @TableField("setting_temperature")
    @Schema(description = "默认温度")
    private BigDecimal settingTemperature;

    /**
     * 默认最大 tokens
     */
    @TableField("setting_max_tokens")
    @Schema(description = "默认最大 tokens")
    private Integer settingMaxTokens;

    /**
     * 默认请求超时（秒）
     */
    @TableField("setting_timeout")
    @Schema(description = "默认请求超时(秒)")
    private Integer settingTimeout;

    /**
     * 是否启用缓存
     */
    @TableField("setting_enable_cache")
    @Schema(description = "是否启用缓存")
    private Boolean settingEnableCache;

    /**
     * 是否流式输出
     */
    @TableField("setting_stream")
    @Schema(description = "是否流式输出")
    private Boolean settingStream;

    /**
     * RAG 向量模型
     */
    @TableField("rag_embedding_model")
    @Schema(description = "RAG 向量模型")
    private String ragEmbeddingModel;

    /**
     * RAG 问答 LLM 模型
     */
    @TableField("rag_llm_model")
    @Schema(description = "RAG 问答 LLM 模型")
    private String ragLlmModel;

    /**
     * RAG OCR 识别模型
     */
    @TableField("rag_ocr_model")
    @Schema(description = "RAG OCR 识别模型")
    private String ragOcrModel;

    /**
     * RAG 上传文件目录
     */
    @TableField("rag_upload_dir")
    @Schema(description = "RAG 上传文件目录")
    private String ragUploadDir;

    /**
     * RAG 向量数据库
     */
    @TableField("rag_vector_storage")
    @Schema(description = "RAG 向量数据库")
    private String ragVectorStorage;

    /**
     * 更新时间
     */
    @TableField(value = "setting_update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime settingUpdateTime;
}
