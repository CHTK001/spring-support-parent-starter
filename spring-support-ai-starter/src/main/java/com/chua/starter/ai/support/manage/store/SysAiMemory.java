package com.chua.starter.ai.support.manage.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 记忆体条目（跨会话长期记忆）。
 *
 * <p>持久化到 {@code sys_ai_memory} 表。与 {@link SysAiCompanionMemory}（数字伙伴专属记忆）
 * 的区别是：本表按登录用户归属，可在任意会话中被召回注入，是通用对话记忆。</p>
 *
 * <p>字段口径：{@code scope} 为 {@code global} 时 {@code conversationId} 为空并对该用户全部会话生效，
 * 为 {@code conversation} 时仅在所属会话内生效；{@code strength} 随时间衰减、随召回命中回补；
 * {@code dedupKey} 是内容归一化指纹，用于写入时合并同义记忆。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
@TableName("sys_ai_memory")
public class SysAiMemory implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /**
     * 作用域：全局记忆（跨会话生效）
     */
    public static final String SCOPE_GLOBAL = "global";

    /**
     * 作用域：会话记忆（仅所属会话生效）
     */
    public static final String SCOPE_CONVERSATION = "conversation";

    /**
     * 状态：可被召回
     */
    public static final String STATUS_ACTIVE = "active";

    /**
     * 状态：已归档（不参与召回，保留可查）
     */
    public static final String STATUS_ARCHIVED = "archived";

    /**
     * 状态：已过期
     */
    public static final String STATUS_EXPIRED = "expired";

    /**
     * 来源：用户或接口显式写入
     */
    public static final String SOURCE_MANUAL = "manual";

    /**
     * 来源：轮次结束后的自动抽取
     */
    public static final String SOURCE_AUTO_EXTRACT = "auto-extract";

    /**
     * 记忆 标识（uuidv7 字符串）
     */
    @TableId(value = "sys_ai_memory_id", type = IdType.INPUT)
    private String memoryId; // 记忆标识

    /**
     * 归属用户 标识（服务端按当前登录用户写入，不接受客户端传值）
     */
    @TableField("sys_ai_memory_user_id")
    private String userId; // 归属用户

    /**
     * 作用域：global/conversation
     */
    @TableField("sys_ai_memory_scope")
    private String scope; // 作用域

    /**
     * 所属会话 标识（scope=conversation 时非空）
     */
    @TableField("sys_ai_memory_conversation_id")
    private String conversationId; // 所属会话

    /**
     * 种类：preference/fact/instruction/episode
     */
    @TableField("sys_ai_memory_kind")
    private String kind; // 种类

    /**
     * 摘要标题
     */
    @TableField("sys_ai_memory_title")
    private String title; // 摘要标题

    /**
     * 记忆内容
     */
    @TableField("sys_ai_memory_content")
    private String content; // 内容

    /**
     * 检索关键词（逗号分隔，避免 JSON 列在部分库上的重复转义问题）
     */
    @TableField("sys_ai_memory_keywords")
    private String keywords; // 检索关键词

    /**
     * 重要度 0-1
     */
    @TableField("sys_ai_memory_importance")
    private Double importance; // importance

    /**
     * 强度 0-1
     */
    @TableField("sys_ai_memory_strength")
    private Double strength; // strength

    /**
     * 被召回次数
     */
    @TableField("sys_ai_memory_hit_count")
    private Integer hitCount; // 命中次数

    /**
     * 是否置顶
     */
    @TableField("sys_ai_memory_pinned")
    private Boolean pinned; // 是否置顶

    /**
     * 状态：active/archived/expired
     */
    @TableField("sys_ai_memory_status")
    private String status; // 状态

    /**
     * 来源：manual/auto-extract/tool/import
     */
    @TableField("sys_ai_memory_source")
    private String source; // 来源

    /**
     * 去重指纹
     */
    @TableField("sys_ai_memory_dedup_key")
    private String dedupKey; // 去重指纹

    /**
     * 创建时间（毫秒）
     */
    @TableField("sys_ai_memory_created_at")
    private Long createdAt; // 创建at

    /**
     * 更新时间（毫秒）
     */
    @TableField("sys_ai_memory_updated_at")
    private Long updatedAt; // 更新at

    /**
     * 最近召回时间（毫秒）
     */
    @TableField("sys_ai_memory_last_used_at")
    private Long lastUsedAt; // 最近召回at

    /**
     * 过期时间（毫秒，空=永不过期）
     */
    @TableField("sys_ai_memory_expires_at")
    private Long expiresAt; // 过期at
}
