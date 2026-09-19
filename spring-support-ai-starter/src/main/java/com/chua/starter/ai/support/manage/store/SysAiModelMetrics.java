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

/**
 * 全量模型指标库（数据源：Artificial Analysis）。
 *
 * <p>承载指标源能采集到的全部模型指标，与本地「配置了哪些模型」无关；
 * 配置模型（sys_ai_provider_model）不持有指标，计费/展示按模型名（{@link #modelKey}）
 * 关联本表取价。被 {@link #manualLocked} 标记的字段在全量同步时不覆盖。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_ai_model_metrics", autoResultMap = true)
public class SysAiModelMetrics implements Serializable {

    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 主键
     */
    @TableId(value = "sys_ai_model_metrics_id", type = IdType.AUTO)
    private Long id; // 标识

    /**
     * 归一化模型键（唯一，关联键）
     */
    @TableField("model_key")
    private String modelKey; // 模型键

    /**
     * 模型名称（展示用）
     */
    @TableField("model_name")
    private String modelName; // 模型名称

    /**
     * 厂商（可空）
     */
    @TableField("provider")
    private String provider; // 厂商

    /**
     * 输入单价（USD/百万 token）
     */
    @TableField("price_input_per_musd")
    private BigDecimal priceInputPerMusd; // 输入价

    /**
     * 输出单价（USD/百万 token）
     */
    @TableField("price_output_per_musd")
    private BigDecimal priceOutputPerMusd; // 输出价

    /**
     * 缓存命中输入价（USD/百万 token）
     */
    @TableField("price_cache_hit_per_musd")
    private BigDecimal priceCacheHitPerMusd; // 缓存命中价

    /**
     * 缓存写入价（USD/百万 token）
     */
    @TableField("price_cache_write_per_musd")
    private BigDecimal priceCacheWritePerMusd; // 缓存写入价

    /**
     * 智能指数
     */
    @TableField("intelligence_index")
    private BigDecimal intelligenceIndex; // 智能指数

    /**
     * 输出速度（token/秒）
     */
    @TableField("output_speed_tps")
    private BigDecimal outputSpeedTps; // 输出速度

    /**
     * 首 token 延迟（秒）
     */
    @TableField("latency_first_token_seconds")
    private BigDecimal latencyFirstTokenSeconds; // 首token延迟

    /**
     * 上下文窗口（token 数）
     */
    @TableField("context_window_tokens")
    private Long contextWindowTokens; // 上下文窗口

    /**
     * 图标地址
     */
    @TableField("icon_url")
    private String iconUrl; // 图标

    /**
     * 是否支持深度思考（推理）
     */
    @TableField("reasoning")
    private Boolean reasoning; // 深度思考

    /**
     * 是否支持联网检索
     */
    @TableField("web_search")
    private Boolean webSearch; // 联网检索

    /**
     * 是否支持图片输入（视觉）
     */
    @TableField("image_input")
    private Boolean imageInput; // 图片输入

    /**
     * 是否支持函数调用
     */
    @TableField("function_calling")
    private Boolean functionCalling; // 函数调用

    /**
     * 手动锁定字段名集合（同步时这些字段不覆盖）
     */
    @TableField(value = "manual_locked", typeHandler = JacksonTypeHandler.class)
    private List<String> manualLocked; // 锁定字段

    /**
     * 手动修改时间（毫秒）
     */
    @TableField("manual_updated_at")
    private Long manualUpdatedAt; // 手动修改时间

    /**
     * 手动修改人
     */
    @TableField("manual_updated_by")
    private String manualUpdatedBy; // 手动修改人

    /**
     * 数据源标识
     */
    @TableField("source")
    private String source; // 数据源

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
