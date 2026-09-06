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
 * 桌面伙伴技能（Companion Skill）实体。
 *
 * <p>持久化到 {@code sys_ai_companion_skill} 表，技能严格按伙伴归属（companion_id），
 * 前端契约见 {@code ICompanionSkill}。{@code content} 字段保存 SKILL.md 全文。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_ai_companion_skill", autoResultMap = true)
public class SysAiCompanionSkill implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 技能主键（UUIDv7 字符串）
     */
    @TableId(value = "sys_ai_companion_skill_id", type = IdType.INPUT)
    private String skillId;

    /**
     * 所属伙伴 ID（null=legacy legacy row）
     */
    @TableField("sys_ai_companion_skill_companion_id")
    private String companionId;

    /**
     * 技能名
     */
    @TableField("sys_ai_companion_skill_name")
    private String skillName;

    /**
     * 状态：draft/active/archived
     */
    @TableField("sys_ai_companion_skill_status")
    private String status;

    /**
     * 来源
     */
    @TableField("sys_ai_companion_skill_source")
    private String source;

    /**
     * 置信度
     */
    @TableField("sys_ai_companion_skill_confidence")
    private Double confidence;

    /**
     * 事件 ID（JSON 数组）
     */
    @TableField(value = "sys_ai_companion_skill_provenance_event_ids", typeHandler = JacksonTypeHandler.class)
    private List<String> provenanceEventIds;

    /**
     * 强度
     */
    @TableField("sys_ai_companion_skill_strength")
    private Double strength;

    /**
     * 版本
     */
    @TableField("sys_ai_companion_skill_version")
    private Integer version;

    /**
     * 技能模式 ID
     */
    @TableField("sys_ai_companion_skill_skill_pattern_id")
    private String skillPatternId;

    /**
     * 使用次数
     */
    @TableField("sys_ai_companion_skill_usage_count")
    private Integer usageCount;

    /**
     * 最后使用时间（epoch 毫秒）
     */
    @TableField("sys_ai_companion_skill_last_used_at")
    private Long lastUsedAt;

    /**
     * 创建时间（epoch 毫秒）
     */
    @TableField("sys_ai_companion_skill_created_at")
    private Long createdAt;

    /**
     * 更新时间（epoch 毫秒）
     */
    @TableField("sys_ai_companion_skill_updated_at")
    private Long updatedAt;

    /**
     * SKILL.md frontmatter 描述（CompanionSkillView）
     */
    @TableField("sys_ai_companion_skill_description")
    private String description;

    /**
     * SKILL.md 全文
     */
    @TableField("sys_ai_companion_skill_content")
    private String content;
}