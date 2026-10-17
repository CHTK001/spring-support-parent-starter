package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 记忆抽取上下文，一轮对话结束后交给 {@code MemoryExtractor} SPI 的入参。
 *
 * @param userId         归属用户标识
 * @param conversationId 会话标识
 * @param userMessage    本轮用户输入
 * @param assistantReply 本轮模型回复
 * @param model          本轮使用的模型名称
 * @author CH
 * @since 4.0.0.42
 */
@Schema(name = "记忆抽取上下文")
public record MemoryExtractionContext(
        @Schema(description = "归属用户标识") String userId,
        @Schema(description = "会话标识") String conversationId,
        @Schema(description = "本轮用户输入") String userMessage,
        @Schema(description = "本轮模型回复") String assistantReply,
        @Schema(description = "本轮使用的模型名称") String model) implements Serializable {

    private static final long serialVersionUID = 1L;
}
