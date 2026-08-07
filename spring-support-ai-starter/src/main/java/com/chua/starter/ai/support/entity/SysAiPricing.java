package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 定价表（官方价格）
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("sys_ai_pricing")
public class SysAiPricing {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 服务商
     */
    private String provider;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 输入价格（每1k tokens，单位元）
     */
    private Double inputPricePer1k;

    /**
     * 输出价格（每1k tokens，单位元）
     */
    private Double outputPricePer1k;

    /**
     * 币种：CNY/USD
     */
    private String currency;

    /**
     * 生效日期
     */
    private LocalDateTime effectiveDate;

    /**
     * 来源：manual（手动）/ sync（官方同步）
     */
    private String source;

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
}

