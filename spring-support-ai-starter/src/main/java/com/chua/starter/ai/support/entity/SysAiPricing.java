package com.chua.starter.ai.support.entity;

import com.chua.starter.mybatis.pojo.SysBase;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
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
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long sysAiPricingId;

    /**
     * 服务商
     */
    private String sysAiPricingProvider;

    /**
     * 模型名称
     */
    private String sysAiPricingModel;

    /**
     * 输入价格（每 1k tokens，单位元）
     */
    private Double sysAiPricingInputPricePer1k;

    /**
     * 输出价格（每 1k tokens，单位元）
     */
    private Double sysAiPricingOutputPricePer1k;

    /**
     * 币种：CNY / USD
     */
    private String sysAiPricingCurrency;

    /**
     * 生效日期
     */
    private LocalDateTime sysAiPricingEffectiveDate;

    /**
     * 来源：manual（手动）/ sync（官方同步）
     */
    private String sysAiPricingSource;
}
