package com.chua.starter.ai.support.nomifun.store;

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
 * <p>对应前端 {@code /api/providers/{id}/models} 契约，
 * 存储提供商下的模型能力信息，包含任务能力列表。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_nomifun_provider_model", autoResultMap = true)
public class NomifunProviderModel implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 所属提供商ID
     */
    @TableField("provider_id")
    private String providerId;

    /**
     * 模型名称
     */
    @TableId(value = "model", type = IdType.INPUT)
    private String model;

    /**
     * 是否启用
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 排序序号
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 模型描述
     */
    @TableField("description")
    private String description;

    /**
     * 任务能力JSON数组
     */
    @TableField(value = "capabilities", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> capabilities;

    /**
     * 模型指标JSON（延迟/智能/输出速度等）
     */
    @TableField(value = "metrics", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metrics;

    /**
     * 创建时间（毫秒）
     */
    @TableField("create_time")
    private Long createdAt;

    /**
     * 更新时间（毫秒）
     */
    @TableField("update_time")
    private Long updatedAt;
}