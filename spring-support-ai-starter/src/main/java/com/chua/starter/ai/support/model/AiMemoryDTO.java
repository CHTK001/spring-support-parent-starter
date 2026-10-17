package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * 记忆体条目，列表与召回出口的统一出参。
 *
 * <p>{@code score} 仅在召回类出口回填，表示本轮的实际打分（重要度 × 衰减强度 × 命中加成 × 相关性），
 * 列表出口为 {@code null}。</p>
 *
 * @param memoryId       记忆标识
 * @param userId         归属用户标识（管理员列表用于区分归属）
 * @param scope          作用域：global/conversation
 * @param conversationId 所属会话标识
 * @param kind           种类：preference/fact/instruction/episode
 * @param title          摘要标题
 * @param content        记忆内容
 * @param keywords       检索关键词
 * @param importance     重要度 0-1
 * @param strength       强度 0-1
 * @param score          本轮召回打分，仅召回出口回填
 * @param hitCount       被召回次数
 * @param pinned         是否置顶
 * @param status         状态：active/archived/expired
 * @param source         来源：manual/auto-extract/tool/import
 * @param createdAt      创建时间（毫秒）
 * @param updatedAt      更新时间（毫秒）
 * @param lastUsedAt     最近召回时间（毫秒）
 * @param expiresAt      过期时间（毫秒，空=永不过期）
 * @author CH
 * @since 4.0.0.42
 */
@Schema(name = "记忆体条目")
public record AiMemoryDTO(
        @Schema(description = "记忆标识") String memoryId,
        @Schema(description = "归属用户标识（管理员列表用于区分归属）") String userId,
        @Schema(description = "作用域：global/conversation") String scope,
        @Schema(description = "所属会话标识") String conversationId,
        @Schema(description = "种类：preference/fact/instruction/episode") String kind,
        @Schema(description = "摘要标题") String title,
        @Schema(description = "记忆内容") String content,
        @Schema(description = "检索关键词") List<String> keywords,
        @Schema(description = "重要度 0-1") Double importance,
        @Schema(description = "强度 0-1") Double strength,
        @Schema(description = "本轮召回打分，仅召回出口回填") Double score,
        @Schema(description = "被召回次数") Integer hitCount,
        @Schema(description = "是否置顶") Boolean pinned,
        @Schema(description = "状态：active/archived/expired") String status,
        @Schema(description = "来源：manual/auto-extract/tool/import") String source,
        @Schema(description = "创建时间（毫秒）") Long createdAt,
        @Schema(description = "更新时间（毫秒）") Long updatedAt,
        @Schema(description = "最近召回时间（毫秒）") Long lastUsedAt,
        @Schema(description = "过期时间（毫秒，空=永不过期）") Long expiresAt) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 回填本轮召回打分，返回新的实例。
     *
     * @param score 本轮打分
     * @return 带打分的结果
     */
    public AiMemoryDTO withScore(Double score) {
        return new AiMemoryDTO(memoryId, userId, scope, conversationId, kind, title, content, keywords,
                importance, strength, score, hitCount, pinned, status, source,
                createdAt, updatedAt, lastUsedAt, expiresAt);
    }
}
