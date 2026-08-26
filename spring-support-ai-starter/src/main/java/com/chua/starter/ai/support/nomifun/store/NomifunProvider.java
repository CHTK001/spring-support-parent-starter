package com.chua.starter.ai.support.nomifun.store;

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
 * {@code credentials} 字段写入后不回显，响应仅以 {@code has_credentials} 布尔标识。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_nomifun_provider", autoResultMap = true)
public class NomifunProvider implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 提供商ID（UUID）
     */
    @TableId(value = "provider_id", type = IdType.INPUT)
    private String providerId;

    /**
     * 平台类型（openai/anthropic/google等）
     */
    @TableField("platform")
    private String platform;

    /**
     * 提供商名称
     */
    @TableField("name")
    private String name;

    /**
     * API基础URL
     */
    @TableField("base_url")
    private String baseUrl;

    /**
     * 认证方式（api_key/bearer/oauth/none）
     */
    @TableField("auth_scheme")
    private String authScheme;

    /**
     * 凭证JSON（写入后不回显）
     */
    @TableField(value = "credentials", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> credentials;

    /**
     * 是否启用
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * AWS Bedrock配置JSON
     */
    @TableField(value = "bedrock_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> bedrockConfig;

    /**
     * 排序序号
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 创建时间（毫秒）
     */
    @TableField("create_time")
    private Long createdAt;

    /**
     * 更新时间（毫秒）
     */
    @TableField("update_time")
    private Long updatedAt;
}