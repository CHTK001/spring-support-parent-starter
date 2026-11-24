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

    /**
     * 计费模式：token（按 token）/ image（按张/按次）
     */
    @TableField("sys_ai_pricing_billing_mode")
    private String billingMode; // 计费模式

    /**
     * 缓存读单价（每 1k tokens，USD）
     */
    @TableField("sys_ai_pricing_cache_read_per_1k")
    private Double cacheReadPer1k; // 缓存读单价

    /**
     * 缓存写单价（每 1k tokens，USD）
     */
    @TableField("sys_ai_pricing_cache_write_per_1k")
    private Double cacheWritePer1k; // 缓存写单价

    /**
     * 按次计价基准价（USD/次，图像等按张计费模型）
     */
    @TableField("sys_ai_pricing_per_request_price")
    private Double perRequestPrice; // 按次计价

    /**
     * 分档计价 JSON：
     * {billingMode, basis, tiers:[{label, minTokens, maxTokens, inputPer1k, outputPer1k,
     * cacheReadPer1k, cacheWritePer1k, cacheWrite1hPer1k, perRequestPrice}]}
     * （价格折算每 1k，USD）
     */
    @TableField("sys_ai_pricing_tiers")
    private String tiers; // 分档计价 JSON

    /**
     * 峰值时段价 JSON：{enabled, start, end, multiplier}
     */
    @TableField("sys_ai_pricing_peak")
    private String peak; // 峰值时段价 JSON

    /**
     * 同步来源分组名（sub2api 分组成员价）
     */
    @TableField("sys_ai_pricing_group_name")
    private String groupName; // 来源分组名

    /**
     * 分组倍率（分组价 = 官方价 × 倍率）
     */
    @TableField("sys_ai_pricing_rate_multiplier")
    private Double rateMultiplier; // 分组倍率

    /**
     * 同模型跨分组差异 JSON：[{group, rateMultiplier, inputPer1k, outputPer1k, perRequestPrice}]
     * （价格折算每 1k，USD）
     */
    @TableField("sys_ai_pricing_group_quotes")
    private String groupQuotes; // 跨分组差异 JSON

    /**
     * 官方价对照 JSON：{inputPer1k, outputPer1k, cacheReadPer1k, cacheWritePer1k,
     * tiers:[{label, inputPer1k, outputPer1k, perRequestPrice}]}（折算每 1k，USD）
     */
    @TableField("sys_ai_pricing_official")
    private String official; // 官方价对照 JSON
}
