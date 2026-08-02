package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 试卷VO
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class PaperVO {

    private Long id;
    private String name;
    private String subject;
    private String gradeLevel;
    private String grade;
    private Integer totalScore;
    private Integer duration;
    private Integer questionCount;
    private Integer status;
    private String source;
    private LocalDateTime createTime;

    private List<QuestionVO> questions;
}