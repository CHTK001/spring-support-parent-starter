package com.chua.starter.eduai.model.dto;

import java.util.List;

/**
 * 学生在线作答提交DTO
 *
 * <p>学生身份、班级归属与考试关联都由服务端按<b>登录态 + 布置关系</b>推导：
 * 客户端只传逐题作答内容，不接受客户端自称学生或指定考试 —— 否则既能冒名替考，
 * 也能把卷子提交到任意考试上。</p>
 *
 * @param content 答题内容JSON（可空，随答题卡一并落库）
 * @param answers 逐题作答列表（题目 ID + 作答内容）
 * @since 4.0.0.43
 * @author CH
 */
public record StudentPaperSubmitDTO(
        String content,
        List<MarkingSubmitDTO.AnswerItem> answers) {
}
