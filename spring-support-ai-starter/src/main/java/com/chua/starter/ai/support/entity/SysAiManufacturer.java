package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 厂商实体，对应表 {@code sys_ai_manufacturer}。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_manufacturer")
public class SysAiManufacturer {

    /**
     * 厂商主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Integer sysAiManufacturerId;

    /**
     * 厂商编码（如 openai / zhipu / baidu）
     */
    @TableField("sys_ai_manufacturer_code")
    private String sysAiManufacturerCode;

    /**
     * 厂商名称
     */
    @TableField("sys_ai_manufacturer_name")
    private String sysAiManufacturerName;

    /**
     * 厂商工厂类型（如 openai / zhipu / anthropic）
     */
    @TableField("sys_ai_manufacturer_factory")
    private String sysAiManufacturerFactory;

    /**
     * 厂商 API 基础地址
     */
    @TableField("sys_ai_manufacturer_base_url")
    private String sysAiManufacturerBaseUrl;

    /**
     * 厂商应用 Key
     */
    @TableField("sys_ai_manufacturer_app_key")
    private String sysAiManufacturerAppKey;

    /**
     * 厂商应用 Secret
     */
    @TableField("sys_ai_manufacturer_api_secret")
    private String sysAiManufacturerApiSecret;

    /**
     * 是否兼容 OpenAI 协议：1 是，0 否
     */
    @TableField("sys_ai_manufacturer_openai_compatible")
    private Integer sysAiManufacturerOpenaiCompatible;

    /**
     * 厂商 Logo 地址
     */
    @TableField("sys_ai_manufacturer_logo")
    private String sysAiManufacturerLogoUrl;

    /**
     * 厂商备注描述
     */
    @TableField("sys_ai_manufacturer_remark")
    private String sysAiManufacturerDescription;

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("sys_ai_manufacturer_status")
    private Integer sysAiManufacturerStatus;

    /**
     * 排序值
     */
    @TableField("sys_ai_manufacturer_sort")
    private Integer sysAiManufacturerSort;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}

