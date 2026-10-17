package com.chua.starter.eduai.model.vo;

/**
 * 题目VO
 *
 * @param id         标识
 * @param type       类型
 * @param stem       Stem
 * @param options    选项
 * @param answer     Answer
 * @param analysis   Analysis
 * @param score      分数
 * @param questionNo 试卷题唯一编号（手动组卷题项，二维码内容）
 * @param order      试卷内顺序（手动组卷题项）
 * @param section    题组/大题标识（手动组卷题项）
 * @since 4.0.0.42
 * @author CH
 */
public record QuestionVO(
        Long id,
        String type,
        String stem,
        String options,
        String answer,
        String analysis,
        Integer score,
        String questionNo,
        Integer order,
        String section) {
}
