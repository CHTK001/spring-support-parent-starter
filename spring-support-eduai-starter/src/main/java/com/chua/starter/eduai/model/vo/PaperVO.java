package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 试卷VO
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class PaperVO {

    /** 试卷标识 */
    private Long paperId;
    /** 试卷名称 */
    private String paperName;
    /** 学科 */
    private String paperSubject;
    /** 学段 */
    private String paperGradeLevel;
    /** 年级 */
    private String paperGrade;
    /** 总分 */
    private Integer paperTotalScore;
    /** 考试时长（分钟） */
    private Integer paperDuration;
    /** 题目数量 */
    private Integer paperQuestionCount;
    /** 状态 */
    private Integer paperStatus;
    /** 来源 */
    private String paperSource;
    /** 创建时间 */
    private LocalDateTime paperCreateTime;

    /** 试卷内题目列表 */
    private List<QuestionVO> questions;
}