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
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 技能主键（uuidv7 字符串）
     */
    @TableId(value = "sys_ai_companion_skill_id", type = IdType.INPUT)
    private String skillId; // skillid

    /**
     * 所属伙伴 标识（空=legacy legacy row）
     */
    @TableField("sys_ai_companion_skill_companion_id")
    private String companionId; // companionid

    /**
     * 技能名
     */
    @TableField("sys_ai_companion_skill_name")
    private String skillName; // skill名称

    /**
     * 状态：draft/活跃/archived
     */
    @TableField("sys_ai_companion_skill_status")
    private String status; // 状态

    /**
     * 来源
     */
    @TableField("sys_ai_companion_skill_source")
    private String source; // 源

    /**
     * 置信度
     */
    @TableField("sys_ai_companion_skill_confidence")
    private Double confidence; // 信心

    /**
     * 事件 标识（JSON 数组）
     */
    @TableField(value = "sys_ai_companion_skill_provenance_event_ids", typeHandler = JacksonTypeHandler.class)
    private List<String> provenanceEventIds; // provenance事件标识

    /**
     * 强度
     */
    @TableField("sys_ai_companion_skill_strength")
    private Double strength; // strength

    /**
     * 版本
     */
    @TableField("sys_ai_companion_skill_version")
    private Integer version; // 版本

    /**
     * 技能模式 标识
     */
    @TableField("sys_ai_companion_skill_skill_pattern_id")
    private String skillPatternId; // skill模式标识

    /**
     * 使用次数
     */
    @TableField("sys_ai_companion_skill_usage_count")
    private Integer usageCount; // usage数量

    /**
     * 最后使用时间（轮次 毫秒）
     */
    @TableField("sys_ai_companion_skill_last_used_at")
    private Long lastUsedAt; // 最后一个usedat

    /**
     * 创建时间（轮次 毫秒）
     */
    @TableField("sys_ai_companion_skill_created_at")
    private Long createdAt; // 创建at

    /**
     * 更新时间（轮次 毫秒）
     */
    @TableField("sys_ai_companion_skill_updated_at")
    private Long updatedAt; // 更新at

    /**
     * SKILL.md frontmatter 描述（companionskillview）
     */
    @TableField("sys_ai_companion_skill_description")
    private String description; // description

    /**
     * SKILL.md 全文
     */
    @TableField("sys_ai_companion_skill_content")
    private String content; // 内容

    /**
     * 归属用户标识（随所属伙伴写入，非客户端传入）
     */
    @TableField("sys_ai_companion_skill_user_id")
    private String userId; // 归属用户
}
