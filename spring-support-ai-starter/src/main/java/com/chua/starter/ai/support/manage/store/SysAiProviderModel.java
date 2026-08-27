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
 * <p>对应前端 {@code /api/provider-models} 契约，复合主键
 * ({@code sys_ai_provider_model_provider_id}, {@code sys_ai_provider_model_model})。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_ai_provider_model", autoResultMap = true)
public class SysAiProviderModel implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 所属提供商ID
     */
    @TableField("sys_ai_provider_model_provider_id")
    private String sysAiProviderModelProviderId;

    /**
     * 模型名称
     */
    @TableId(value = "sys_ai_provider_model_model", type = IdType.INPUT)
    private String sysAiProviderModelModel;

    /**
     * 是否启用
     */
    @TableField("sys_ai_provider_model_enabled")
    private Boolean sysAiProviderModelEnabled;

    /**
     * 排序序号
     */
    @TableField("sys_ai_provider_model_sort_order")
    private Integer sysAiProviderModelSortOrder;

    /**
     * 模型描述
     */
    @TableField("sys_ai_provider_model_description")
    private String sysAiProviderModelDescription;

    /**
     * 任务能力JSON数组
     */
    @TableField(value = "sys_ai_provider_model_capabilities", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> sysAiProviderModelCapabilities;

    /**
     * 模型指标JSON（延迟/智能/输出速度等）
     */
    @TableField(value = "sys_ai_provider_model_metrics", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> sysAiProviderModelMetrics;

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_ai_provider_model_create_time")
    private Long sysAiProviderModelCreateTime;

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_ai_provider_model_update_time")
    private Long sysAiProviderModelUpdateTime;
}