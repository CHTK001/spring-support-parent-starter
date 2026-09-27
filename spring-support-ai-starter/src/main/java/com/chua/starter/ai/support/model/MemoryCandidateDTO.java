package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 候选记忆，抽取 SPI 的出参；落库前由存储层做归一化、去重与归属盖章。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(name = "候选记忆")
public class MemoryCandidateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

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

    @Schema(description = "作用域：global/conversation，缺 global")
    private String scope;

    @Schema(description = "有效天数，空或 0 表示永不过期")
    private Integer ttlDays;
}
