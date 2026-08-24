package com.chua.starter.ai.support.nomifun.store;

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
 * <p>会话主键为 {@code conversationId}（UUIDv7 字符串），由后端生成，前端不传。
 * 持久化到 {@code sys_nomifun_conversation} 表。</p>
 *
 * <p>JSON 字段说明：
 * <ul>
 *   <li>{@code model} / {@code extra} 为结构化 Map，通过 {@link JacksonTypeHandler}
 *       序列化为 TEXT 存储（列名保持不变）；</li>
 *   <li>{@code runtime} 为运行时状态，不入库（{@link TableField#exist()}=false）。</li>
 * </ul>
 * </p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_nomifun_conversation")
public class Conversation implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 会话主键（UUIDv7 字符串），由后端 {@code newId()} 生成并填充
     */
    @TableId(value = "conversation_id", type = IdType.INPUT)
    private String conversationId;

    /**
     * 会话类型（当前仅 nomi）
     */
    private String type = "nomi";

    /**
     * 会话名称
     */
    private String name;

    /**
     * 绑定的模型信息：{provider_id, model, use_model?}，JSON 序列化存储
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> model;

    /**
     * 扩展字段（合并语义，前端 extra 字段透传存储），JSON 序列化存储
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;

    /**
     * 是否置顶
     */
    private boolean pinned = false;

    /**
     * 置顶时间（epoch 毫秒，未置顶为 null）
     */
    private Long pinnedAt;

    /**
     * 创建时间（epoch 毫秒）
     */
    private Long createdAt;

    /**
     * 修改时间（epoch 毫秒）
     */
    private Long modifiedAt;

    /**
     * 关联的执行模板 ID
     */
    private String executionTemplateId;

    /**
     * 运行时状态（如 active_turn_id），仅内存使用，不持久化
     */
    @TableField(exist = false)
    private Map<String, Object> runtime;
}