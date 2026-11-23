package com.chua.starter.eduai.model.dto;

import java.util.List;

/**
 * AI 出题请求 DTO。
 *
 * <p>与 {@link QuestionAiSelectDTO} 的区别：选题是让模型从<b>已有题库</b>里挑题号；
 * 出题是让模型<b>原创</b>题干/选项/答案/解析，返回尚未入库的草稿，由教师审核修订后
 * 走既有 save 链路入库（与手工新增同一审核口径）。</p>
 *
 * @param subject          学科
 * @param gradeLevel       学段；码值（PRIMARY/JUNIOR/…）与中文名都接受，服务端统一归一化为中文名
 * @param grade            年级
 * @param types            限定题型列表；为空表示不限，由模型按学科合理搭配
 * @param difficulty       目标难度 1-5；为空表示由模型按内容自定
 * @param knowledgePointIds 知识点标识列表；为空表示不限知识点
 * @param count            期望题目数（1-20，越界由服务端收敛）
 * @author CH
 * @since 4.0.0.51
 */
public record QuestionAiGenerateDTO(
        String subject,
        String gradeLevel,
        String grade,
        List<String> types,
        Integer difficulty,
        List<Long> knowledgePointIds,
        Integer count) {
}
