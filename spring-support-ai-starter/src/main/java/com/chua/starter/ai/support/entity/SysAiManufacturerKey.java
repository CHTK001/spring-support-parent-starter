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
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_manufacturer_key")
public class SysAiManufacturerKey {

    /**
     * 厂商 Key 主键 标识
     */
    @TableId(value = "sys_ai_manufacturer_key_id", type = IdType.AUTO)
    /**
     * sysaimanufacturer密钥标识
    */
    private Long id;

    /**
     * 厂商编码（关联 sys_AI_manufacturer.sys_AI_manufacturer_编码）
     */
    @TableField("sys_ai_manufacturer_code")
    /**
     * sysaimanufacturer代码
    */
    private String sysAiManufacturerCode;

    /**
     * 厂商名称
     */
    @TableField("sys_ai_manufacturer_name")
    /**
     * sysaimanufacturer名称
    */
    private String sysAiManufacturerName;

    /**
     * API Key
     */
    @TableField("sys_ai_manufacturer_key_value")
    private String value; // 值

    /**
     * API Secret
     */
    @TableField("sys_ai_manufacturer_key_secret")
    private String secret; // secret

    /**
     * 状态：1 启用，0 停用，-1 禁用
     */
    @TableField("sys_ai_manufacturer_key_status")
    private Integer status; // 状态

    /**
     * 权重（用于多 Key 轮询）
     */
    @TableField("sys_ai_manufacturer_key_weight")
    private Integer weight; // 权重

    /**
     * 限流标记时间戳（毫秒）
     */
    @TableField("sys_ai_manufacturer_key_rate_limited_at")
    private Long rateLimitedAt; // rate限制at

    /**
     * 备注
     */
    @TableField("sys_ai_manufacturer_key_remark")
    private String remark; // 评论

    /**
     * 创建人名称
     */
    @TableField(fill = FieldFill.INSERT)
    /**
     * 创建名称
    */
    private String createName;

    /**
     * 创建人 标识
     */
    @TableField(fill = FieldFill.INSERT)
    /**
     * 创建by
    */
    private Integer createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    /**
     * 创建时间
    */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
    */
    private LocalDateTime updateTime;

    /**
     * 更新人名称
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新名称
    */
    private String updateName;

    /**
     * 更新人 标识
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新by
    */
    private Integer updateBy;
}
