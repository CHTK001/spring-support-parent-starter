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
 * appKey 表，存储供应商接入凭证、次数限制与过期策略。
 *
 * <p>次数限制与过期时间二选一：{@link #appKeyMaxUse} 为次数上限，
 * {@link #appKeyExpireTime} 为过期时间，均为空表示不限。</p>
 *
 * @since 2026-08-03
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_ai_app_key")
@Schema(name = "AppKey")
public class SysAiAppKey extends SysBase {

    /**
     * appKey 主键 标识
     */
    /**
     * appKey 主键 标识
     */
    @TableId(value = "sys_ai_app_key_id", type = IdType.AUTO)
    @Schema(description = "AppKey 主键 ID")
    /** APP密钥标识 */
    private Long appKeyId;

    /**
    * appKey 名称
    */
    /**
     * appKey 名称
     */
    @TableField("sys_ai_app_key_name")
    @Schema(description = "AppKey 名称")
    /** APP密钥名称 */
    private String appKeyName;

    /**
    * appKey 密钥值
    */
    /**
     * appKey 密钥值
     */
    @TableField("sys_ai_app_key_value")
    @Schema(description = "AppKey 密钥值")
    /** APP密钥值 */
    private String appKeyValue;

    /**
    * 关联供应商 标识
    */
    /**
     * 关联供应商 标识
     */
    @TableField("sys_ai_app_key_provider_id")
    @Schema(description = "关联供应商 ID")
    /** APP密钥提供者标识 */
    private Long appKeyProviderId;

    /**
    * 限定使用的模型（可空）
    */
    /**
     * 限定使用的模型
     */
    @TableField("sys_ai_app_key_model")
    @Schema(description = "限定使用的模型")
    /** APP密钥模型 */
    private String appKeyModel;

    /**
    * 绑定分组 标识
    */
    /**
     * 绑定分组 标识
     */
    @TableField("sys_ai_app_key_group_id")
    @Schema(description = "绑定分组 ID")
    /** APP密钥分组标识 */
    private Long appKeyGroupId;

    /**
    * 是否启用
    */
    /**
     * 是否启用
     */
    @TableField("sys_ai_app_key_enabled")
    @Schema(description = "是否启用")
    /** APP密钥是否启用 */
    private Boolean appKeyEnabled;

    /**
    * 已使用次数
    */
    /**
     * 已使用次数
     */
    @TableField("sys_ai_app_key_use_count")
    @Schema(description = "已使用次数")
    /** APP密钥USE数量 */
    private Long appKeyUseCount;

    /**
    * 次数上限（空 表示不限）
    */
    /**
     * 次数上限
     */
    @TableField("sys_ai_app_key_max_use")
    @Schema(description = "次数上限")
    /** APP密钥最大值USE */
    private Long appKeyMaxUse;

    /**
    * 过期时间（空 表示不限）
    */
    /**
     * 过期时间
     */
    @TableField("sys_ai_app_key_expire_datetime")
    @Schema(description = "过期时间")
    /** APP密钥expire时间 */
    private LocalDateTime appKeyExpireTime;

    /**
    * 逻辑删除标记
    */
    /**
     * 逻辑删除标记
     */
    @TableField("sys_ai_app_key_delete")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    /** APP密钥删除标记 */
    private Integer appKeyDeleted;
}
