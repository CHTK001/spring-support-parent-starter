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
* AI 提供商连接持久化实体。
*
* <p>对应前端 {@code /api/providers/{id}/connections} 契约，
* 存储提供商的附加连接信息，凭证写入后不回显。</p>
*
* <p>数据库列以表名 {@code sys_ai_provider_connection_} 作为前缀，实体字段保持简短语义名，
* 通过 {@link TableField} 显式映射。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName(value = "sys_ai_provider_connection", autoResultMap = true)
public class SysAiProviderConnection implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
    * 连接标识（UUID）
    */
    @TableId(value = "sys_ai_provider_connection_id", type = IdType.INPUT)
    private String connectionId; // connectionid

    /**
    * 所属提供商标识
    */
    @TableField("sys_ai_provider_connection_provider_id")
    private String providerId; // 提供者标识

    /**
    * 连接角色（默认/backup等）
    */
    @TableField("sys_ai_provider_connection_role")
    private String role; // 角色

    /**
    * 连接标签
    */
    @TableField("sys_ai_provider_connection_label")
    private String label; // 标签

    /**
    * 覆盖的基础URL
    */
    @TableField("sys_ai_provider_connection_base_url")
    private String baseUrl; // baseurl

    /**
    * 覆盖的认证方式
    */
    @TableField("sys_ai_provider_connection_auth_scheme")
    private String authScheme; // 认证scheme

    /**
    * 凭证JSON（写入后不回显）
    */
    @TableField(value = "sys_ai_provider_connection_credentials", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> credentials; // 凭证

    /**
    * 扩展字段JSON
    */
    @TableField(value = "sys_ai_provider_connection_extra", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra; // extra

    /**
    * 创建时间（毫秒）
    */
    @TableField("sys_ai_provider_connection_create_time")
    private Long createdAt; // 创建at

    /**
    * 更新时间（毫秒）
    */
    @TableField("sys_ai_provider_connection_update_time")
    private Long updatedAt; // 更新at
}
