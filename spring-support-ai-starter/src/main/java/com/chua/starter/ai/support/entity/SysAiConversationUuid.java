package com.chua.starter.ai.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话 UUID 映射实体，对应表 {@code sys_ai_conversation_uuid}。
 *
 * <p>持久化 UUIDv7 ↔ dialogId(Long) 映射，确保重启后会话可恢复。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_conversation_uuid")
public class SysAiConversationUuid {

    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * UUIDv7（前端会话 ID）
     */
    @TableField("uuid")
    private String uuid;

    /**
     * 对话 ID（AIDialog 自增 Long）
     */
    @TableField("dialog_id")
    private Long dialogId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
