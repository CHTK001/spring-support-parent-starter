package com.chua.starter.eduai.model.dto;

/**
 * 手动组卷-加题项DTO
 *
 * @param questionId 题目标识
 * @param score 该题分值，允许为空（草稿态按 0 处理）
 *
 * @since 4.0.0.42
 * @author CH
 */
public record PaperQuestionItemDTO(
        Long questionId,
        Integer score
) {
}
