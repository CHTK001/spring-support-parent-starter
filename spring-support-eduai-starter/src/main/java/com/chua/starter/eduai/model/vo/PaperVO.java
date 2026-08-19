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

    /** ID */
    private Long id;
    /** 名称 */
    private String name;
    /** Subject */
    private String subject;
    /** Grade级别 */
    private String gradeLevel;
    /** Grade */
    private String grade;
    /** 总数分数 */
    private Integer totalScore;
    /** 持续时间 */
    private Integer duration;
    /** Question数量 */
    private Integer questionCount;
    /** 状态 */
    private Integer status;
    /** 来源 */
    private String source;
    /** Create时间 */
    private LocalDateTime createTime;

    /** Questions */
    private List<QuestionVO> questions;
}