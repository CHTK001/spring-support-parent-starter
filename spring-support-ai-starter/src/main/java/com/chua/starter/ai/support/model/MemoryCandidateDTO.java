package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * 候选记忆，抽取 SPI 的出参；落库前由存储层做归一化、去重与归属盖章。
 *
 * @param kind       种类：preference/fact/instruction/episode
 * @param title      摘要标题
 * @param content    记忆内容
 * @param keywords   检索关键词
 * @param importance 重要度 0-1
 * @param scope      作用域：global/conversation，缺 global
 * @param ttlDays    有效天数，空或 0 表示永不过期
 * @author CH
 * @since 4.0.0.42
 */
@Schema(name = "候选记忆")
public record MemoryCandidateDTO(
        @Schema(description = "种类：preference/fact/instruction/episode") String kind,
        @Schema(description = "摘要标题") String title,
        @Schema(description = "记忆内容") String content,
        @Schema(description = "检索关键词") List<String> keywords,
        @Schema(description = "重要度 0-1") Double importance,
        @Schema(description = "作用域：global/conversation，缺 global") String scope,
        @Schema(description = "有效天数，空或 0 表示永不过期") Integer ttlDays) implements Serializable {

    private static final long serialVersionUID = 1L;
}
