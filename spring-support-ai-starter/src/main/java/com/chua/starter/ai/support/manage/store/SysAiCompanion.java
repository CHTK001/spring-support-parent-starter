package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 桌面伙伴（Companion）实体。
 *
 * <p>持久化到 {@code sys_ai_companion} 表，主键为 UUIDv7 字符串。
 * 复杂嵌套结构（persona/model/voice/learn/evolve/skills/appearance）以 JSON 列存储，
 * 与前端 {@code ICompanionProfile} 契约对齐。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_ai_companion", autoResultMap = true)
public class SysAiCompanion implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 伙伴主键（UUIDv7 字符串）
     */
    @TableId(value = "sys_ai_companion_id", type = IdType.INPUT)
    private String companionId;

    /**
     * 展示序号（正的本地显式序号）
     */
    @TableField("sys_ai_companion_seq")
    private Integer seq;

    /**
     * 伙伴名称
     */
    @TableField("sys_ai_companion_name")
    private String name;

    /**
     * 角色 ID（mochi/ink/roux/pixel/bolt/boo）
     */
    @TableField("sys_ai_companion_character")
    private String character;

    /**
     * persona JSON（preset/custom）
     */
    @TableField(value = "sys_ai_companion_persona_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> persona;

    /**
     * model JSON（provider_id/model/use_model）
     */
    @TableField(value = "sys_ai_companion_model_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> model;

    /**
     * fallback model JSON
     */
    @TableField(value = "sys_ai_companion_fallback_model_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> fallbackModel;

    /**
     * vision model JSON
     */
    @TableField(value = "sys_ai_companion_vision_model_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> visionModel;

    /**
     * voice JSON（asr/tts/vad）
     */
    @TableField(value = "sys_ai_companion_voice_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> voice;

    /**
     * learn JSON（enabled/interval_minutes/model）
     */
    @TableField(value = "sys_ai_companion_learn_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> learn;

    /**
     * evolve JSON
     */
    @TableField(value = "sys_ai_companion_evolve_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> evolve;

    /**
     * skills JSON（enabled/disabled_auto）
     */
    @TableField(value = "sys_ai_companion_skills_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> skills;

    /**
     * appearance JSON（window config）
     */
    @TableField(value = "sys_ai_companion_appearance_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> appearance;

    /**
     * applied preset JSON
     */
    @TableField(value = "sys_ai_companion_applied_preset_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> appliedPreset;

    /**
     * 用户选择的侧栏位置序号
     */
    @TableField("sys_ai_companion_order_index")
    private Integer orderIndex;

    /**
     * 专属会话 ID（每个伙伴一条 nomi 会话）
     */
    @TableField("sys_ai_companion_conversation_id")
    private String conversationId;

    /**
     * 创建时间（epoch 毫秒）
     */
    @TableField("sys_ai_companion_created_at")
    private Long createdAt;

    /**
     * 更新时间（epoch 毫秒）
     */
    @TableField("sys_ai_companion_updated_at")
    private Long updatedAt;
}