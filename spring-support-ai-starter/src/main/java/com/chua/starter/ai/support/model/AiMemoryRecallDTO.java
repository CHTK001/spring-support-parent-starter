package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * 记忆召回结果：本轮实际注入的提示词片段与命中的记忆条目。
 *
 * @param promptBlock    注入到 system 段的文本，无可注入内容时为空串
 * @param items          命中的记忆条目（按分数倒序，含 score）
 * @param limit          本次召回上限条数
 * @param candidateCount 候选池条数（打分前的可见记忆数）
 * @param injected       是否真实注入了对话请求
 * @author CH
 * @since 4.0.0.42
 */
@Schema(name = "记忆召回结果")
public record AiMemoryRecallDTO(
        @Schema(description = "注入到 system 段的文本，无可注入内容时为空串") String promptBlock,
        @Schema(description = "命中的记忆条目（按分数倒序，含 score）") List<AiMemoryDTO> items,
        @Schema(description = "本次召回上限条数") Integer limit,
        @Schema(description = "候选池条数（打分前的可见记忆数）") Integer candidateCount,
        @Schema(description = "是否真实注入了对话请求") Boolean injected) implements Serializable {

    private static final long serialVersionUID = 1L;
}
