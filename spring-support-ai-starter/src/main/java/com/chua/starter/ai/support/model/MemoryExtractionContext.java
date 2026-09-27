package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 记忆抽取上下文，一轮对话结束后交给 {@code MemoryExtractor} SPI 的入参。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(name = "记忆抽取上下文")
public class MemoryExtractionContext implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "归属用户标识")
    private String userId;

    @Schema(description = "会话标识")
    private String conversationId;

    @Schema(description = "本轮用户输入")
    private String userMessage;

    @Schema(description = "本轮模型回复")
    private String assistantReply;

    @Schema(description = "本轮使用的模型名称")
    private String model;
}
