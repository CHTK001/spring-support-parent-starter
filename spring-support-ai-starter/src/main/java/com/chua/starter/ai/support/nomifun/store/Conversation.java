package com.chua.starter.ai.support.nomifun.store;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 前端会话实体（对齐前端 {@code TChatConversation}）。
 *
 * <p>会话主键为 {@code conversationId}（UUIDv7 字符串），由后端生成，前端不传。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class Conversation implements Serializable {

    /**
     * 会话主键（UUIDv7 字符串）
     */
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
     * 绑定的模型信息：{provider_id, model, use_model?}
     */
    private Map<String, Object> model;

    /**
     * 扩展字段（合并语义，前端 extra 字段透传存储）
     */
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
     * 运行时状态（如 active_turn_id）
     */
    private Map<String, Object> runtime;
}
