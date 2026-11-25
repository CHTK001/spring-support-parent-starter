package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 模型价格库（按模型键独立于指标业务维护）。
 *
 * <p>价格数据原先散落在 {@link SysAiModelMetrics} 的价格列中，与指标业务耦合；
 * 本表把「价格」拆为独立业务：输入/输出/缓存命中/缓存写单价（USD/百万 token）、
 * 单任务成本、峰谷定价规则，以及价格侧的手动锁定与更新审计。
 * 指标表旧价格列仅作历史回退，不再新增写入。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
@TableName(value = "sys_ai_model_price", autoResultMap = true)
public class SysAiModelPrice implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 主键
     */
    @TableId(value = "sys_ai_model_price_id", type = IdType.AUTO)
    private Long id; // 标识

    /**
     * 归一化模型键（唯一，关联键）
     */
    @TableField("sys_ai_model_price_model_key")
    private String modelKey; // 模型键

    /**
     * 模型名称（展示用）
     */
    @TableField("sys_ai_model_price_model")
    private String modelName; // 模型名称

    /**
     * 厂商（可空）
     */
    @TableField("sys_ai_model_price_provider")
    private String provider; // 厂商

    /**
     * 输入单价（USD/百万 token）
     */
    @TableField("sys_ai_model_price_input")
    private BigDecimal priceInput; // 输入价

    /**
     * 输出单价（USD/百万 token）
     */
    @TableField("sys_ai_model_price_output")
    private BigDecimal priceOutput; // 输出价

    /**
     * 缓存命中输入价（USD/百万 token）
     */
    @TableField("sys_ai_model_price_cache_hit")
    private BigDecimal priceCacheHit; // 缓存命中价

    /**
     * 缓存写入价（USD/百万 token）
     */
    @TableField("sys_ai_model_price_cache_write")
    private BigDecimal priceCacheWrite; // 缓存写价

    /**
     * 单任务平均成本（USD）
     */
    @TableField("sys_ai_model_price_cost_per_task")
    private BigDecimal costPerTask; // 单任务成本

    /**
     * 峰谷 / 按 N 次定价规则
     */
    @TableField(value = "sys_ai_model_price_pricing_rules", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> pricingRules; // 定价规则

    /**
     * 价格侧手动锁定字段名（价格字段键名，如 price_input_per_musd）
     */
    @TableField(value = "sys_ai_model_price_manual_locked", typeHandler = JacksonTypeHandler.class)
    private List<String> manualLocked; // 手动锁定字段

    /**
     * 价格手动更新时间（毫秒）
     */
    @TableField("sys_ai_model_price_manual_updated_at")
    private Long manualUpdatedAt; // 手动更新时间

    /**
     * 价格手动更新人
     */
    @TableField("sys_ai_model_price_manual_updated_by")
    private String manualUpdatedBy; // 手动更新人

    /**
     * 数据源标识（upstream:/models / catalog 等）
     */
    @TableField("sys_ai_model_price_source")
    private String source; // 数据源

    /**
     * 状态（1 启用）
     */
    @TableField("sys_ai_model_price_status")
    private Integer status; // 状态

    /**
     * 创建时间（毫秒）
     */
    @TableField("create_time")
    private Long createTime; // 创建时间

    /**
     * 更新时间（毫秒）
     */
    @TableField("update_time")
    private Long updateTime; // 更新时间
}
