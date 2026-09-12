package com.chua.starter.eduai.model.dto;

import lombok.Data;

import java.util.List;

/**
* 提交答题卡DTO
*
* @since 4.0.0.42
* @author CH
 */
@Data
public class MarkingSubmitDTO {

    /**
    * 考试标识
     */
    private Long examId;

    /**
    * 学生标识
     */
    private String studentId;

    /**
    * 答题内容JSON
     */
    private String content;

    /**
    * 答题列表
     */
    private List<AnswerItem> answers;

    @Data
    /**
    * answeritem类。
    *
    * @author CH
    * @since 4.0.0
     */
    public static class AnswerItem {
        /** questionid */
        private Long questionId;
        /** Answer */
        private String answer;
    }
}