package com.chua.starter.ai.support.manage.store;

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
 * AI 提供商模型持久化实体。
 *
 * <p>对应前端 {@code /api/provider-models} 契约，复合主键为
 * ({@code sys_ai_provider_model_provider_id}, {@code sys_ai_provider_model_model})。</p>
 *
 * <p>数据库列以表名 {@code sys_ai_provider_model_} 作为前缀，实体字段保持简短语义名，
 * 通过 {@link TableField} 显式映射。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_ai_provider_model", autoResultMap = true)
public class SysAiProviderModel implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 所属提供商标识
     */
    @TableField("sys_ai_provider_model_provider_id")
    private String providerId; // 提供者标识

    /**
     * 模型名称
     */
    @TableId(value = "sys_ai_provider_model_model", type = IdType.INPUT)
    private String model; // 模型

    /**
     * 模型别名（可空，空则显示真实 model id）
    */
    @TableField("sys_ai_provider_model_alias")
    private String alias; // 别名

    /**
     * 是否启用
     */
    @TableField("sys_ai_provider_model_enabled")
    private Boolean enabled; // 已启用

    /**
     * 排序序号
     */
    @TableField("sys_ai_provider_model_sort_order")
    private Integer sortOrder; // 排序订单

    /**
     * 模型描述
     */
    @TableField("sys_ai_provider_model_description")
    private String description; // description


    @TableField("sys_ai_provider_model_context_window")
    /**
     * 上下文Window
    */
    private Integer contextWindow;

    @TableField("sys_ai_provider_model_max_tokens")
    /**
     * 最大值Tokens
    */
    private Integer maxTokens;

    @TableField("sys_ai_provider_model_temperature")
    /**
     * temperature
    */
    private Double temperature;

    @TableField("sys_ai_provider_model_top_p")
    /**
     * 顶部P
    */
    private Double topP;

    @TableField("sys_ai_provider_model_frequency_penalty")
    /**
     * frequencyPenalty
    */
    private Double frequencyPenalty;
    /**
     * 任务能力JSON数组
     */
    @TableField(value = "sys_ai_provider_model_capabilities", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> capabilities; // capabilities

    /**
     * 模型指标JSON（延迟/智能/输出速度等）
     */
    @TableField(value = "sys_ai_provider_model_metrics", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metrics; // 指标

    /**
     * 模型系列（family，Models.dev 回填）
     */
    @TableField("sys_ai_provider_model_family")
    private String family; // 模型系列

    /**
     * 知识截止日期（knowledge，Models.dev 回填）
     */
    @TableField("sys_ai_provider_model_knowledge")
    private String knowledge; // 知识截止

    /**
     * 发布日期（release_date，Models.dev 回填）
     */
    @TableField("sys_ai_provider_model_release_date")
    private String releaseDate; // 发布日期

    /**
     * 最后更新时间（last_updated；指标无该字段时用点击价格同步的时间）
     */
    @TableField("sys_ai_provider_model_last_updated")
    private String lastUpdated; // 最后更新时间

    /**
     * 输入/输出模态（modalities，Models.dev 回填）
     */
    @TableField(value = "sys_ai_provider_model_modalities", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> modalities; // 模态

    /**
     * 是否开源权重（open_weights，Models.dev 回填）
     */
    @TableField("sys_ai_provider_model_open_weights")
    private Boolean openWeights; // 开源权重

    /**
     * 是否支持附件（attachment）
     */
    @TableField("sys_ai_provider_model_attachment")
    private Boolean attachment; // 附件支持

    /**
     * 是否支持深度思考（reasoning）
     */
    @TableField("sys_ai_provider_model_reasoning")
    private Boolean reasoning; // 深度思考

    /**
     * 是否支持工具调用（tool_call / function_calling）
     */
    @TableField("sys_ai_provider_model_tool_call")
    private Boolean toolCall; // 工具调用

    /**
     * 是否支持温度调节（temperature）
     */
    @TableField("sys_ai_provider_model_temperature_supported")
    private Boolean temperatureSupported; // 温度调节支持

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_ai_provider_model_create_time")
    private Long createdAt; // 创建at

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_ai_provider_model_update_time")
    private Long updatedAt; // 更新at
}
