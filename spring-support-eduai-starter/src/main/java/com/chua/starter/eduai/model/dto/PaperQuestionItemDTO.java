package com.chua.starter.eduai.model.dto;

/**
 * 手动组卷-加题项DTO
 *
 * <p>大题标题（{@code section}）省略时，服务端按题型自动归并生成
 * 「一、选择题」「二、判断题」等中文大题块，保留真实试卷的分区版式。
 * 显式传值则优先使用（前端自定义大题名时用）。</p>
 *
 * @param questionId 题目标识
 * @param score 该题分值，允许为空（草稿态按 0 处理）
 * @param section 大题标题（可选）：省略按题型自动生成，显式传则优先
 *
 * @since 4.0.0.42
 * @author CH
 */
public record PaperQuestionItemDTO(
        Long questionId,
        Integer score,
        String section
) {
}
