package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 桌面伙伴记忆（Companion Memory）实体。
 *
 * <p>持久化到 {@code sys_ai_companion_memory} 表。记忆严格按伙伴归属（companion_id），
 * 前端契约见 {@code ICompanionMemory}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_ai_companion_memory", autoResultMap = true)
public class SysAiCompanionMemory implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 记忆主键（UUIDv7 字符串）
     */
    @TableId(value = "sys_ai_companion_memory_id", type = IdType.INPUT)
    private String memoryId;

    /**
     * 所属伙伴 ID（null=legacy legacy row）
     */
    @TableField("sys_ai_companion_memory_companion_id")
    private String companionId;

    /**
     * 记忆类型：profile/preference/knowledge/episode/task/affective
     */
    @TableField("sys_ai_companion_memory_kind")
    private String kind;

    /**
     * 记忆内容
     */
    @TableField("sys_ai_companion_memory_content")
    private String content;

    /**
     * 标签（JSON 数组）
     */
    @TableField(value = "sys_ai_companion_memory_tags", typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /**
     * 重要性
     */
    @TableField("sys_ai_companion_memory_importance")
    private Double importance;

    /**
     * 强度
     */
    @TableField("sys_ai_companion_memory_strength")
    private Double strength;

    /**
     * 是否置顶
     */
    @TableField("sys_ai_companion_memory_pinned")
    private Boolean pinned;

    /**
     * 来源
     */
    @TableField("sys_ai_companion_memory_source")
    private String source;

    /**
     * 状态：active/archived
     */
    @TableField("sys_ai_companion_memory_status")
    private String status;

    /**
     * 创建时间（epoch 毫秒）
     */
    @TableField("sys_ai_companion_memory_created_at")
    private Long createdAt;

    /**
     * 更新时间（epoch 毫秒）
     */
    @TableField("sys_ai_companion_memory_updated_at")
    private Long updatedAt;

    /**
     * 最后强化时间（epoch 毫秒）
     */
    @TableField("sys_ai_companion_memory_last_reinforced_at")
    private Long lastReinforcedAt;
}