package com.chua.starter.eduai.model.vo;

import java.math.BigDecimal;

/**
 * 阅卷完成预览的单题清单。
 *
 * @param questionId 题目标识
 * @param stem       题干摘要（只取展示片段，避免列表过长）
 * @param finalScore 最终分（老师分优先，否则 AI 分）
 * @param maxScore   该题满分
 * @param scoreRate  得分率（0~1，四舍五入到百分之一）
 * @param wrong      是否会被登记进错题本（按当前判错阈值）
 * @since 4.0.0.42
 * @author CH
 */
public record MarkingPreviewQuestionVO(
        Long questionId,
        String stem,
        int finalScore,
        int maxScore,
        BigDecimal scoreRate,
        boolean wrong) {
}
