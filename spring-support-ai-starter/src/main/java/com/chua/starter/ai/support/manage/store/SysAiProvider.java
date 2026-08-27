package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * AI 提供商持久化实体。
 *
 * <p>对应前端 {@code /api/providers} 契约，存储提供商的基础信息。
 * {@code credentials} 字段写入后不回显，响应仅以 {@code has_credentials} 布尔标识。
 * 表 {@code sys_ai_provider}，所有列以表名作为前缀。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_ai_provider", autoResultMap = true)
public class SysAiProvider implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 提供商ID（UUID）
     */
    @TableId(value = "sys_ai_provider_id", type = IdType.INPUT)
    private String sysAiProviderId;

    /**
     * 平台类型（openai/anthropic/google等）
     */
    @TableField("sys_ai_provider_platform")
    private String sysAiProviderPlatform;

    /**
     * 提供商名称
     */
    @TableField("sys_ai_provider_name")
    private String sysAiProviderName;

    /**
     * API基础URL
     */
    @TableField("sys_ai_provider_base_url")
    private String sysAiProviderBaseUrl;

    /**
     * 认证方式（api_key/bearer/oauth/none）
     */
    @TableField("sys_ai_provider_auth_scheme")
    private String sysAiProviderAuthScheme;

    /**
     * 凭证JSON（写入后不回显）
     */
    @TableField(value = "sys_ai_provider_credentials", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> sysAiProviderCredentials;

    /**
     * 是否启用
     */
    @TableField("sys_ai_provider_enabled")
    private Boolean sysAiProviderEnabled;

    /**
     * AWS Bedrock配置JSON
     */
    @TableField(value = "sys_ai_provider_bedrock_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> sysAiProviderBedrockConfig;

    /**
     * 排序序号
     */
    @TableField("sys_ai_provider_sort_order")
    private Integer sysAiProviderSortOrder;

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_ai_provider_create_time")
    private Long sysAiProviderCreateTime;

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_ai_provider_update_time")
    private Long sysAiProviderUpdateTime;
}