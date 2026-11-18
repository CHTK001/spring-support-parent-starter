package com.chua.starter.account.channel.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 渠道管理的渠道模型行（模型级开关）。
 *
 * <p><b>归属</b>：渠道管理属于账号模块。本实体原先位于 AI 模块
 * （{@code SysAiProviderModel}，表 {@code sys_ai_provider_model}），随渠道管理整体归还账号模块：
 * 表 {@code sys_account_channel_model}，列以 {@code sys_account_channel_model_} 为前缀。</p>
 *
 * <p>复合主键为 ({@code sys_account_channel_model_provider_id},
 * {@code sys_account_channel_model_model})。同步上游目录只写行、是否对外暴露由
 * {@code enabled} 决定；免费渠道只取 {@code enabled=1} 且所属渠道 {@code enabled=1} 的模型。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_account_channel_model", autoResultMap = true)
public class SysAccountChannelModel implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 所属渠道标识
     */
    @TableField("sys_account_channel_model_provider_id")
    private String providerId;

    /**
     * 模型名称（对上游的 model id）
     */
    @TableId(value = "sys_account_channel_model_model", type = IdType.INPUT)
    private String model;

    /**
     * 模型别名（可空，空则显示真实 model id）
     */
    @TableField("sys_account_channel_model_alias")
    private String alias;

    /**
     * 模型是否启用
     */
    @TableField("sys_account_channel_model_enabled")
    private Boolean enabled;

    /**
     * 排序序号
     */
    @TableField("sys_account_channel_model_sort_order")
    private Integer sortOrder;

    /**
     * 模型描述
     */
    @TableField("sys_account_channel_model_description")
    private String description;

    /**
     * 上下文窗口
     */
    @TableField("sys_account_channel_model_context_window")
    private Integer contextWindow;

    /**
     * 最大输出 Token 数
     */
    @TableField("sys_account_channel_model_max_tokens")
    private Integer maxTokens;

    /**
     * 温度
     */
    @TableField("sys_account_channel_model_temperature")
    private Double temperature;

    /**
     * Top-P 采样
     */
    @TableField("sys_account_channel_model_top_p")
    private Double topP;

    /**
     * 频率惩罚
     */
    @TableField("sys_account_channel_model_frequency_penalty")
    private Double frequencyPenalty;

    /**
     * 任务能力 JSON 数组
     */
    @TableField(value = "sys_account_channel_model_capabilities", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> capabilities;

    /**
     * 模型指标 JSON（延迟/智能/输出速度等）
     */
    @TableField(value = "sys_account_channel_model_metrics", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metrics;

    /**
     * 模型系列（family，Models.dev 回填）
     */
    @TableField("sys_account_channel_model_family")
    private String family;

    /**
     * 知识截止日期（knowledge，Models.dev 回填）
     */
    @TableField("sys_account_channel_model_knowledge")
    private String knowledge;

    /**
     * 发布日期（release_date，Models.dev 回填）
     */
    @TableField("sys_account_channel_model_release_date")
    private String releaseDate;

    /**
     * 最后更新时间（last_updated）
     */
    @TableField("sys_account_channel_model_last_updated")
    private String lastUpdated;

    /**
     * 输入/输出模态（modalities，Models.dev 回填）
     */
    @TableField(value = "sys_account_channel_model_modalities", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> modalities;

    /**
     * 是否开源权重（open_weights）
     */
    @TableField("sys_account_channel_model_open_weights")
    private Boolean openWeights;

    /**
     * 是否支持附件
     */
    @TableField("sys_account_channel_model_attachment")
    private Boolean attachment;

    /**
     * 是否支持深度思考
     */
    @TableField("sys_account_channel_model_reasoning")
    private Boolean reasoning;

    /**
     * 是否支持工具调用
     */
    @TableField("sys_account_channel_model_tool_call")
    private Boolean toolCall;

    /**
     * 是否支持温度调节
     */
    @TableField("sys_account_channel_model_temperature_supported")
    private Boolean temperatureSupported;

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_account_channel_model_create_time")
    private Long createdAt;

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_account_channel_model_update_time")
    private Long updatedAt;
}
