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
* {@code credentials} 字段写入后不回显，响应仅以 {@code has_credentials} 布尔标识。</p>
*
* <p>数据库列以表名 {@code sys_ai_provider_} 作为前缀，实体字段保持简短语义名，
* 通过 {@link TableField} 显式映射，前端契约稳定不变。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName(value = "sys_ai_provider", autoResultMap = true)
public class SysAiProvider implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
    * 提供商标识（UUID）
    */
    @TableId(value = "sys_ai_provider_id", type = IdType.INPUT)
    private String providerId; // 提供者标识

    /**
    * 平台类型（openai/anthropic/google等）
    */
    @TableField("sys_ai_provider_platform")
    private String platform; // platform

    /**
    * 提供商名称
    */
    @TableField("sys_ai_provider_name")
    private String name; // 名称

    /**
    * API基础URL
    */
    @TableField("sys_ai_provider_base_url")
    private String baseUrl; // baseurl

    /**
    * 认证方式（api_Key/bearer/OAuth/无）
    */
    @TableField("sys_ai_provider_auth_scheme")
    private String authScheme; // 认证scheme

    /**
    * 凭证JSON（写入后不回显）
    */
    @TableField(value = "sys_ai_provider_credentials", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> credentials; // 凭证

    /**
    * 是否启用
    */
    @TableField("sys_ai_provider_enabled")
    private Boolean enabled; // 已启用

    /**
    * AWS Bedrock配置JSON
    */
    @TableField(value = "sys_ai_provider_bedrock_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> bedrockConfig; // bedrock配置

    /**
    * 扩展配置JSON（如 {@code {"custom_headers": {"X-Key": "value"}}}）
    */
    @TableField(value = "sys_ai_provider_extra", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra; // extra

    /**
    * HTTP 代理地址（如 {@code http://127.0.0.1:7890}、{@code socks5://127.0.0.1:1080}），
    * 用于 Google 等无法直连的厂商单独走代理。
    */
    @TableField("sys_ai_provider_proxy")
    private String proxy; // 代理

    /**
    * 排序序号
    */
    @TableField("sys_ai_provider_sort_order")
    private Integer sortOrder; // 排序订单

    /**
    * 创建时间（毫秒）
    */
    @TableField("sys_ai_provider_create_time")
    private Long createdAt; // 创建at

    /**
    * 更新时间（毫秒）
    */
    @TableField("sys_ai_provider_update_time")
    private Long updatedAt; // 更新at
}
