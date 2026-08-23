package com.chua.starter.ai.support.nomifun.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.Map;

/**
 * nomifun 提供商连接持久化实体。
 *
 * <p>对应前端 {@code /api/providers/{id}/connections} 契约，
 * 存储提供商的附加连接信息，凭证写入后不回显。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_nomifun_provider_connection", autoResultMap = true)
public class NomifunProviderConnection {

    /**
     * 连接ID（UUID）
     */
    @TableId(value = "connection_id", type = IdType.INPUT)
    private String connectionId;

    /**
     * 所属提供商ID
     */
    @TableField("provider_id")
    private String providerId;

    /**
     * 连接角色（default/backup等）
     */
    @TableField("role")
    private String role;

    /**
     * 连接标签
     */
    @TableField("label")
    private String label;

    /**
     * 覆盖的基础URL
     */
    @TableField("base_url")
    private String baseUrl;

    /**
     * 覆盖的认证方式
     */
    @TableField("auth_scheme")
    private String authScheme;

    /**
     * 凭证JSON（写入后不回显）
     */
    @TableField(value = "credentials", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> credentials;

    /**
     * 扩展字段JSON
     */
    @TableField(value = "extra", typeHandler = JacksonTypeHandler.class)
    private Object extra;

    /**
     * 创建时间（毫秒）
     */
    @TableField("created_at")
    private Long createdAt;

    /**
     * 更新时间（毫秒）
     */
    @TableField("updated_at")
    private Long updatedAt;
}