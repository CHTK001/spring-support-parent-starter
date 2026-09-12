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