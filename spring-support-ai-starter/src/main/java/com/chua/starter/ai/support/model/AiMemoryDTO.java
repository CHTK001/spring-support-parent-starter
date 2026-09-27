package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 记忆体条目，列表与召回出口的统一出参。
 *
 * <p>{@code score} 仅在召回类出口回填，表示本轮的实际打分（重要度 × 衰减强度 × 命中加成 × 相关性），
 * 列表出口为 {@code null}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(name = "记忆体条目")
public class AiMemoryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "记忆标识")
    private String memoryId;

    @Schema(description = "归属用户标识（管理员列表用于区分归属）")
    private String userId;

    @Schema(description = "作用域：global/conversation")
    private String scope;

    @Schema(description = "所属会话标识")
    private String conversationId;

    @Schema(description = "种类：preference/fact/instruction/episode")
    private String kind;

    @Schema(description = "摘要标题")
    private String title;

    @Schema(description = "记忆内容")
    private String content;

    @Schema(description = "检索关键词")
    private List<String> keywords;

    @Schema(description = "重要度 0-1")
    private Double importance;

    @Schema(description = "强度 0-1")
    private Double strength;

    @Schema(description = "本轮召回打分，仅召回出口回填")
    private Double score;

    @Schema(description = "被召回次数")
    private Integer hitCount;

    @Schema(description = "是否置顶")
    private Boolean pinned;

    @Schema(description = "状态：active/archived/expired")
    private String status;

    @Schema(description = "来源：manual/auto-extract/tool/import")
    private String source;

    @Schema(description = "创建时间（毫秒）")
    private Long createdAt;

    @Schema(description = "更新时间（毫秒）")
    private Long updatedAt;

    @Schema(description = "最近召回时间（毫秒）")
    private Long lastUsedAt;

    @Schema(description = "过期时间（毫秒，空=永不过期）")
    private Long expiresAt;
}
