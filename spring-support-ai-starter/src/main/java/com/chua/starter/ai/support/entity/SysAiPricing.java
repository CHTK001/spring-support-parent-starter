package com.chua.starter.ai.support.entity;

import com.chua.starter.mybatis.pojo.SysBase;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
* AI 定价表（官方价格）。
*
* @author CH
* @since 4.0.0.42
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_ai_pricing")
public class SysAiPricing extends SysBase {

    /**
    * 主键 标识
     */
    @TableId(value = "sys_ai_pricing_id", type = IdType.AUTO)
    private Long id; // 标识

    /**
    * 服务商
     */
    @TableField("sys_ai_pricing_provider")
    private String provider; // 提供者

    /**
    * 模型名称
     */
    @TableField("sys_ai_pricing_model")
    private String model; // 模型

    /**
    * 输入价格（每 1k 令牌，单位元）
     */
    @TableField("sys_ai_pricing_input_price_per_1k")
    private Double inputPricePer1k; // 输入priceper1k

    /**
    * 输出价格（每 1k 令牌，单位元）
     */
    @TableField("sys_ai_pricing_output_price_per_1k")
    private Double outputPricePer1k; // 输出priceper1k

    /**
    * 币种：CNY / USD
     */
    @TableField("sys_ai_pricing_currency")
    private String currency; // 货币

    /**
    * 生效日期
     */
    @TableField("sys_ai_pricing_effective_date")
    private LocalDateTime effectiveDate; // effective日期

    /**
    * 来源：manual（手动）/ 同步（官方同步）
     */
    @TableField("sys_ai_pricing_source")
    private String source; // 源
}
