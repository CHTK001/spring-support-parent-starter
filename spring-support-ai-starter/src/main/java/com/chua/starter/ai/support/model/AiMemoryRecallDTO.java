package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 记忆召回结果：本轮实际注入的提示词片段与命中的记忆条目。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(name = "记忆召回结果")
public class AiMemoryRecallDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "注入到 system 段的文本，无可注入内容时为空串")
    private String promptBlock;

    @Schema(description = "命中的记忆条目（按分数倒序，含 score）")
    private List<AiMemoryDTO> items;

    @Schema(description = "本次召回上限条数")
    private Integer limit;

    @Schema(description = "候选池条数（打分前的可见记忆数）")
    private Integer candidateCount;

    @Schema(description = "是否真实注入了对话请求")
    private Boolean injected;
}
