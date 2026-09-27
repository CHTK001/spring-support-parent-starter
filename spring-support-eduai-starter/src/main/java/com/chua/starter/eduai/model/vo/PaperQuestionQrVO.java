package com.chua.starter.eduai.model.vo;

/**
 * 试卷题项二维码反查VO
 *
 * @param paperId 所属试卷标识
 * @param paperNo 所属试卷唯一编号
 * @param paperName 所属试卷名称
 * @param paperVersion 试卷当前版本号
 * @param itemVersion 题项所属版本号（题项最后一次随试卷改动的版本）
 * @param questionId 题目标识
 * @param questionNo 题项唯一编号（二维码内容）
 * @param questionType 题型
 * @param questionStem 题干
 * @param questionOptions 选项（JSON 文本）
 * @param questionAnswer 答案
 * @param questionAnalysis 解析
 * @param score 分值
 * @param order 顺序
 * @param section 题组/大题标识
 *
 * @since 4.0.0.42
 * @author CH
 */
public record PaperQuestionQrVO(
        Long paperId,
        String paperNo,
        String paperName,
        Integer paperVersion,
        Integer itemVersion,
        Long questionId,
        String questionNo,
        String questionType,
        String questionStem,
        String questionOptions,
        String questionAnswer,
        String questionAnalysis,
        Integer score,
        Integer order,
        String section
) {
}
