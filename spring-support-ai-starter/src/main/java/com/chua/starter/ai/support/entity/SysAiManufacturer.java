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
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_manufacturer")
public class SysAiManufacturer {

    /**
     * 厂商主键 标识
     */
    @TableId(value = "sys_ai_manufacturer_id", type = IdType.AUTO)
    /** sysaimanufacturerid */
    private Integer id;

    /**
     * 厂商编码（如 openai / zhipu / 百度云）
     */
    @TableField("sys_ai_manufacturer_code")
    private String code; // 编码

    /**
     * 厂商名称
     */
    @TableField("sys_ai_manufacturer_name")
    private String name; // 名称

    /**
     * 厂商工厂类型（如 openai / zhipu / anthropic）
     */
    @TableField("sys_ai_manufacturer_factory")
    private String factory; // 工厂

    /**
     * 厂商 API 基础地址
     */
    @TableField("sys_ai_manufacturer_base_url")
    private String baseUrl; // baseurl

    /**
     * 厂商应用 Key
     */
    @TableField("sys_ai_manufacturer_app_key")
    private String appKey; // appKey

    /**
     * 厂商应用 Secret
     */
    @TableField("sys_ai_manufacturer_api_secret")
    private String apiSecret; // apisecret

    /**
     * 是否兼容 打开AI 协议：1 是，0 否
     */
    @TableField("sys_ai_manufacturer_openai_compatible")
    private Integer openaiCompatible; // openaicompatible

    /**
     * 厂商 Logo 地址
     */
    @TableField("sys_ai_manufacturer_logo")
    private String logoUrl; // logourl

    /**
     * 厂商备注描述
     */
    @TableField("sys_ai_manufacturer_remark")
    private String description; // description

    /**
     * 状态：1 启用，0 停用
     */
    @TableField("sys_ai_manufacturer_status")
    private Integer status; // 状态

    /**
     * 排序值
     */
    @TableField("sys_ai_manufacturer_sort")
    private Integer sort; // 排序

    /**
     * 创建时间
     */
    @TableField("create_time")
    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    /** 更新时间 */
    private LocalDateTime updateTime;
}

