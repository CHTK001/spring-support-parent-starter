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
 * 渠道管理的渠道连接（绑定凭据 / 账号）。
 *
 * <p><b>归属</b>：渠道管理属于账号模块。本实体原先位于 AI 模块
 * （{@code SysAiProviderConnection}，表 {@code sys_ai_provider_connection}），
 * 随渠道管理整体归还账号模块：表 {@code sys_account_channel_connection}，
 * 列以 {@code sys_account_channel_connection_} 为前缀。</p>
 *
 * <p>一个渠道可挂多条连接（default / backup，固定账号 / 临时账号）；
 * 凭据写入后不回显。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_account_channel_connection", autoResultMap = true)
public class SysAccountChannelConnection implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 连接标识（UUID）
     */
    @TableId(value = "sys_account_channel_connection_id", type = IdType.INPUT)
    private String connectionId;

    /**
     * 所属渠道标识
     */
    @TableField("sys_account_channel_connection_provider_id")
    private String providerId;

    /**
     * 连接角色（default/backup 等）
     */
    @TableField("sys_account_channel_connection_role")
    private String role;

    /**
     * 账号类型（{@code FIXED} 固定 / {@code TEMPORARY} 临时）
     */
    @TableField("sys_account_channel_connection_account_type")
    private String accountType;

    /**
     * 连接标签
     */
    @TableField("sys_account_channel_connection_label")
    private String label;

    /**
     * 归属用户（账号隔离：普通用户只看自己绑定的连接；{@code null} 视为管理员/系统账号池）
     */
    @TableField("sys_account_channel_connection_user_id")
    private String userId;

    /**
     * 覆盖的基础地址
     */
    @TableField("sys_account_channel_connection_base_url")
    private String baseUrl;

    /**
     * 覆盖的认证方式
     */
    @TableField("sys_account_channel_connection_auth_scheme")
    private String authScheme;

    /**
     * 凭证 JSON（写入后不回显）
     */
    @TableField(value = "sys_account_channel_connection_credentials", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> credentials;

    /**
     * 扩展字段 JSON
     */
    @TableField(value = "sys_account_channel_connection_extra", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_account_channel_connection_create_time")
    private Long createdAt;

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_account_channel_connection_update_time")
    private Long updatedAt;
}
