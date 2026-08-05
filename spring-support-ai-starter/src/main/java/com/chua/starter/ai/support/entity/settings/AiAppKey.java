package com.chua.starter.ai.support.entity.settings;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AppKey 表，存储供应商接入凭证、次数限制与过期策略。
 *
 * <p>次数限制与过期时间二选一：{@link #appKeyMaxUse} 为次数上限，
 * {@link #appKeyExpireTime} 为过期时间，均为空表示不限。</p>
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_app_key")
@Schema(name = "AppKey")
public class AiAppKey extends SysBase {

    /**
     * AppKey 主键 ID
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "AppKey 主键 ID")
    private Long appKeyId;

    /**
     * AppKey 名称
     */
    @TableField("app_key_name")
    @Schema(description = "AppKey 名称")
    private String appKeyName;

    /**
     * AppKey 密钥值（敏感信息）
     */
    @TableField("app_key_value")
    @Schema(description = "AppKey 密钥值")
    private String appKeyValue;

    /**
     * 关联供应商 ID
     */
    @TableField("app_key_provider_id")
    @Schema(description = "关联供应商 ID")
    private Long appKeyProviderId;

    /**
     * 限定使用的模型（可空）
     */
    @TableField("app_key_model")
    @Schema(description = "限定使用的模型")
    private String appKeyModel;

    /**
     * 绑定分组 ID（分组的 key 只能用分组下的模型）
     */
    @TableField("app_key_group_id")
    @Schema(description = "绑定分组 ID")
    private Long appKeyGroupId;

    /**
     * 是否启用
     */
    @TableField("app_key_enabled")
    @Schema(description = "是否启用")
    private Boolean appKeyEnabled;

    /**
     * 已使用次数
     */
    @TableField("app_key_use_count")
    @Schema(description = "已使用次数")
    private Long appKeyUseCount;

    /**
     * 次数上限（null 表示不限）
     */
    @TableField("app_key_max_use")
    @Schema(description = "次数上限")
    private Long appKeyMaxUse;

    /**
     * 过期时间（null 表示不限）
     */
    @TableField("app_key_expire_time")
    @Schema(description = "过期时间")
    private LocalDateTime appKeyExpireTime;

    /**
     * 逻辑删除标记
     */
    @TableField("app_key_deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer appKeyDeleted;
}