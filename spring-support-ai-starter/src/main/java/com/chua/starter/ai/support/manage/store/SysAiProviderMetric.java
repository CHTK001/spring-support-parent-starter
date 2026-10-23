package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 厂商指标系数表（数据源：Models.dev）。
 *
 * <p>按厂商聚合的兜底指标：模型级定价（AA 缓存 / {@code sys_ai_pricing} / 全量指标库
 * {@code sys_ai_model_metrics}）都查不到时，按厂商键取本表系数做最终预估。
 * 输入/输出价为该厂商收录模型的单价中位数（USD/百万 token），上下文为中位数窗口，
 * 模型数为该厂商在数据源中收录的模型总数。由 {@code syncProviderMetrics()} 全量
 * 同步维护，不随单个模型配置变更。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_ai_provider_metric")
public class SysAiProviderMetric extends SysBase {

    /**
     * 主键 标识
     */
    @TableId(value = "sys_ai_provider_metric_id", type = IdType.AUTO)
    private Long id; // 标识

    /**
     * 厂商键（如 openai、deepseek，唯一）
     */
    @TableField("sys_ai_provider_metric_provider")
    private String provider; // 厂商键

    /**
     * 厂商名称（展示用）
     */
    @TableField("sys_ai_provider_metric_provider_name")
    private String providerName; // 厂商名

    /**
     * 输入单价中位数（USD/百万 token）
     */
    @TableField("sys_ai_provider_metric_price_input")
    private BigDecimal priceInputPerMusd; // 输入价中位数

    /**
     * 输出单价中位数（USD/百万 token）
     */
    @TableField("sys_ai_provider_metric_price_output")
    private BigDecimal priceOutputPerMusd; // 输出价中位数

    /**
     * 上下文窗口中位数（token 数）
     */
    @TableField("sys_ai_provider_metric_context_window")
    private Long contextWindowTokens; // 上下文中位数

    /**
     * 该厂商在数据源中收录的模型数
     */
    @TableField("sys_ai_provider_metric_model_count")
    private Integer modelCount; // 模型数

    /**
     * 数据源标识（如 models.dev）
     */
    @TableField("sys_ai_provider_metric_source")
    private String source; // 数据源
}
