package com.chua.starter.eduai.model.dto;

import java.util.List;

/**
 * AI 选题请求 DTO。
 *
 * <p>供组卷页在「当前试卷已存在」的前提下，直接向 AI 要一批题目补进试卷 ——
 * 与 {@link PaperGenerateDTO} 的区别是本 DTO 不建卷，只返回题目列表。</p>
 *
 * <p>调用方需先通过 {@code GET /v2/edu/ai/capability} 确认 AI 可用；
 * AI 不可用时服务端会回退到规则选题（按学科/学段取候选题），
 * 不会因此报错，但结果不代表模型判断。</p>
 *
 * @param subject          学科
 * @param gradeLevel       学段；码值（PRIMARY/JUNIOR/…）与中文名（小学/初中/…）都接受，服务端统一归一化为中文名
 * @param grade            年级
 * @param types            限定题型；为空表示不限题型
 * @param knowledgePointIds 知识点标识列表；为空表示不限知识点
 * @param count            期望题目数
 * @author CH
 * @since 4.0.0.42
 */
public record QuestionAiSelectDTO(
        String subject,
        String gradeLevel,
        String grade,
        List<String> types,
        List<Long> knowledgePointIds,
        Integer count) {
}
