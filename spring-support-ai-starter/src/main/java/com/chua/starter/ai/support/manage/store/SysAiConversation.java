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
 * 前端会话实体（对齐前端 {@code TChatConversation}），同时作为 MyBatis-Plus ORM 实体。
 *
 * <p>会话主键为 {@code SysAiConversationId}（UUIDv7 字符串），由后端生成，前端不传。
 * 持久化到 {@code sys_ai_conversation} 表。</p>
 *
 * <p>JSON 字段说明：
 * <ul>
 *   <li>{@code sysAiConversationModel} / {@code sysAiConversationExtra} 为结构化 Map，通过
 *       {@link JacksonTypeHandler} 序列化为 TEXT 存储；</li>
 *   <li>{@code sysAiConversationRuntime} 为运行时状态，不入库（{@link TableField#exist()}=false）。</li>
 * </ul>
 * </p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_conversation")
public class SysAiConversation implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 会话主键（UUIDv7 字符串），由后端 {@code newId()} 生成并填充
     */
    @TableId(value = "sys_ai_conversation_id", type = IdType.INPUT)
    private String sysAiConversationId;

    /**
     * 会话类型（当前仅 nomi）
     */
    @TableField("sys_ai_conversation_type")
    private String sysAiConversationType = "nomi";

    /**
     * 会话名称
     */
    @TableField("sys_ai_conversation_name")
    private String sysAiConversationName;

    /**
     * 绑定的模型信息：{provider_id, model, use_model?}，JSON 序列化存储
     */
    @TableField(value = "sys_ai_conversation_model", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> sysAiConversationModel;

    /**
     * 扩展字段（合并语义，前端 extra 字段透传存储），JSON 序列化存储
     */
    @TableField(value = "sys_ai_conversation_extra", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> sysAiConversationExtra;

    /**
     * 是否置顶
     */
    @TableField("sys_ai_conversation_pinned")
    private boolean sysAiConversationPinned = false;

    /**
     * 置顶时间（epoch 毫秒，未置顶为 null）
     */
    @TableField("sys_ai_conversation_pinned_at")
    private Long sysAiConversationPinnedAt;

    /**
     * 创建时间（epoch 毫秒）
     */
    @TableField("sys_ai_conversation_created_at")
    private Long sysAiConversationCreatedAt;

    /**
     * 修改时间（epoch 毫秒）
     */
    @TableField("sys_ai_conversation_modified_at")
    private Long sysAiConversationModifiedAt;

    /**
     * 关联的执行模板 ID
     */
    @TableField("sys_ai_conversation_execution_template_id")
    private String sysAiConversationExecutionTemplateId;

    /**
     * 运行时状态（如 active_turn_id），仅内存使用，不持久化
     */
    @TableField(exist = false)
    private Map<String, Object> sysAiConversationRuntime;
}