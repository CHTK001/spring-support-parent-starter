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
 * 复杂嵌套结构（persona/模型/voice/learn/evolve/skills/appearance）以 JSON 列存储，
 * 与前端 {@code ICompanionProfile} 契约对齐。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName(value = "sys_ai_companion", autoResultMap = true)
public class SysAiCompanion implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 伙伴主键（uuidv7 字符串）
     */
    @TableId(value = "sys_ai_companion_id", type = IdType.INPUT)
    private String companionId; // companionid

    /**
     * 展示序号（正的本地显式序号）
     */
    @TableField("sys_ai_companion_seq")
    private Integer seq; // seq

    /**
     * 伙伴名称
     */
    @TableField("sys_ai_companion_name")
    private String name; // 名称

    /**
     * 角色 标识（mochi/Ink/roux/pixel/螺栓/boo）
     */
    @TableField("sys_ai_companion_character")
    private String character; // character

    /**
     * persona JSON（preset/习俗）
     */
    @TableField(value = "sys_ai_companion_persona_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> persona; // persona

    /**
     * 模型 JSON（提供者_标识/模型/use_模型）
     */
    @TableField(value = "sys_ai_companion_model_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> model; // 模型

    /**
     * 降级 模型 JSON
     */
    @TableField(value = "sys_ai_companion_fallback_model_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> fallbackModel; // 降级模型

    /**
     * vision 模型 JSON
     */
    @TableField(value = "sys_ai_companion_vision_model_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> visionModel; // vision模型

    /**
     * voice JSON（asr/tts/vad）
     */
    @TableField(value = "sys_ai_companion_voice_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> voice; // voice

    /**
     * learn JSON（已启用/间隔_minutes/模型）
     */
    @TableField(value = "sys_ai_companion_learn_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> learn; // learn

    /**
     * evolve JSON
     */
    @TableField(value = "sys_ai_companion_evolve_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> evolve; // evolve

    /**
     * skills JSON（已启用/已禁用_auto）
     */
    @TableField(value = "sys_ai_companion_skills_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> skills; // skills

    /**
     * appearance JSON（窗口 配置）
     */
    @TableField(value = "sys_ai_companion_appearance_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> appearance; // appearance

    /**
     * applied preset JSON
     */
    @TableField(value = "sys_ai_companion_applied_preset_text", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> appliedPreset; // appliedpreset

    /**
     * 用户选择的侧栏位置序号
     */
    @TableField("sys_ai_companion_order_index")
    private Integer orderIndex; // 订单索引

    /**
     * 专属会话 标识（每个伙伴一条 nomi 会话）
     */
    @TableField("sys_ai_companion_conversation_id")
    private String conversationId; // conversationid

    /**
     * 创建时间（轮次 毫秒）
     */
    @TableField("sys_ai_companion_created_at")
    private Long createdAt; // 创建at

    /**
     * 更新时间（轮次 毫秒）
     */
    @TableField("sys_ai_companion_updated_at")
    private Long updatedAt; // 更新at

    /**
     * 归属用户标识（登录用户名，由服务端按当前登录用户写入）
     */
    @TableField("sys_ai_companion_user_id")
    private String userId; // 归属用户
}
