package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 厂商 API Key 实体，对应表 {@code sys_ai_manufacturer_key}。
 *
 * <p>每个厂商可配置多个 API Key，用于轮询与负载均衡（weight 权重、rate_limited_at 限流标记）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_manufacturer_key")
public class SysAiManufacturerKey {

    /**
     * 厂商 Key 主键 ID
     */
    @TableId(value = "sys_ai_manufacturer_key_id", type = IdType.AUTO)
    private Long sysAiManufacturerKeyId;

    /**
     * 厂商编码（关联 sys_ai_manufacturer.sys_ai_manufacturer_code）
     */
    @TableField("sys_ai_manufacturer_code")
    private String sysAiManufacturerCode;

    /**
     * 厂商名称
     */
    @TableField("sys_ai_manufacturer_name")
    private String sysAiManufacturerName;

    /**
     * API Key
     */
    @TableField("sys_ai_manufacturer_key_value")
    private String sysAiManufacturerKeyValue;

    /**
     * API Secret
     */
    @TableField("sys_ai_manufacturer_key_secret")
    private String sysAiManufacturerKeySecret;

    /**
     * 状态：1 启用，0 停用，-1 禁用
     */
    @TableField("sys_ai_manufacturer_key_status")
    private Integer sysAiManufacturerKeyStatus;

    /**
     * 权重（用于多 Key 轮询）
     */
    @TableField("sys_ai_manufacturer_key_weight")
    private Integer sysAiManufacturerKeyWeight;

    /**
     * 限流标记时间戳（毫秒）
     */
    @TableField("sys_ai_manufacturer_key_rate_limited_at")
    private Long sysAiManufacturerKeyRateLimitedAt;

    /**
     * 备注
     */
    @TableField("sys_ai_manufacturer_key_remark")
    private String sysAiManufacturerKeyRemark;

    /**
     * 创建人名称
     */
    @TableField(fill = FieldFill.INSERT)
    private String createName;

    /**
     * 创建人 ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Integer createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 更新人名称
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateName;

    /**
     * 更新人 ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Integer updateBy;
}
