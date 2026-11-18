package com.chua.starter.account.channel.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 渠道管理的渠道行（渠道级开关与上游信息）。
 *
 * <p><b>归属</b>：渠道管理属于账号模块。本实体原先位于 AI 模块
 * （旧实体 {@code SysAiProvider}，旧表 {@code sys_ai_provider}），
 * 随渠道管理整体归还账号模块，类名与表名一并改为账号模块口径：
 * 表 {@code sys_account_channel}，列以 {@code sys_account_channel_} 为前缀。</p>
 *
 * <p>字段语义保持不变（{@code providerId} 仍是渠道主键，前端契约不动），
 * AI 模块只通过本模块的 Bean / SPI 读取渠道数据，不再持有该表。</p>
 *
 * <p>{@code credentials} 字段写入后不回显，响应仅以 {@code has_credentials} 布尔标识。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_account_channel", autoResultMap = true)
public class SysAccountChannel implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 渠道标识（UUID）
     */
    @TableId(value = "sys_account_channel_id", type = IdType.INPUT)
    private String providerId;

    /**
     * 平台类型（openai/anthropic/google 等，也是渠道管理里的渠道键）
     */
    @TableField("sys_account_channel_platform")
    private String platform;

    /**
     * 渠道展示名
     */
    @TableField("sys_account_channel_name")
    private String name;

    /**
     * API 基础地址（免费渠道可为空串）
     */
    @TableField("sys_account_channel_base_url")
    private String baseUrl;

    /**
     * 认证方式（api_key/bearer/oauth/none）
     */
    @TableField("sys_account_channel_auth_scheme")
    private String authScheme;

    /**
     * 凭证 JSON（写入后不回显）
     */
    @TableField(value = "sys_account_channel_credentials", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> credentials;

    /**
     * 渠道是否启用
     */
    @TableField("sys_account_channel_enabled")
    private Boolean enabled;

    /**
     * AWS Bedrock 配置 JSON
     */
    @TableField(value = "sys_account_channel_bedrock_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> bedrockConfig;

    /**
     * 扩展配置 JSON（如 {@code {"custom_headers": {"X-Key": "value"}}}）
     */
    @TableField(value = "sys_account_channel_extra", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;

    /**
     * HTTP 代理地址（如 {@code http://127.0.0.1:7890}、{@code socks5://127.0.0.1:1080}）
     */
    @TableField("sys_account_channel_proxy")
    private String proxy;

    /**
     * 排序序号
     */
    @TableField("sys_account_channel_sort_order")
    private Integer sortOrder;

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_account_channel_create_time")
    private Long createdAt;

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_account_channel_update_time")
    private Long updatedAt;
}
