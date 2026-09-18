package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
* 错题记录实体
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName("edu_wrong_question")
public class EduWrongQuestion {

    /**
    * 错题记录标识
    */
    @TableId(type = IdType.AUTO)
    /** wrongquestionid */
    private Long wrongQuestionId;

    /**
    * 学生标识
    */
    private String wrongQuestionStudentId;

    /**
    * 题目标识
    */
    private Long wrongQuestionQuestionId;

    /**
    * 考试标识
    */
    private Long wrongQuestionExamId;

    /**
    * 错误次数
    */
    private Integer wrongQuestionWrongCount;

    /**
    * 掌握度
    */
    private Integer wrongQuestionMasteryLevel;

    /**
    * 最近错误时间
    */
    private LocalDateTime wrongQuestionLastWrongTime;

    /**
    * 最近复习时间
    */
    private LocalDateTime wrongQuestionLastReviewTime;

    /**
    * 复习次数
    */
    private Integer wrongQuestionReviewCount;

    /**
    * 状态
    */
    private Integer wrongQuestionStatus;

    /**
    * 创建时间
    */
    private LocalDateTime wrongQuestionCreateTime;

    /**
    * 更新时间
    */
    private LocalDateTime wrongQuestionUpdateTime;
}
