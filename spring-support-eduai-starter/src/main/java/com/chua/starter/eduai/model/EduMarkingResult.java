package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
* 阅卷结果实体
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName("edu_marking_result")
public class EduMarkingResult {

    /**
    * 阅卷结果标识
     */
    @TableId(type = IdType.AUTO)
    /** Marking结果标识 */
    private Long markingResultId;

    /**
    * 答题卡标识
     */
    private Long markingResultAnswerSheetId;

    /**
    * 题目标识
     */
    private Long markingResultQuestionId;

    /**
    * 得分
     */
    private Integer markingResultScore;

    /**
    * 评语
     */
    private String markingResultComment;

    /**
    * 批改详情JSON
     */
    private String markingResultDetail;

    /**
    * AI评分
     */
    private Integer markingResultAiScore;

    /**
    * AI评语
     */
    private String markingResultAiComment;

    /**
    * 教师修正后评分
     */
    private Integer markingResultTeacherScore;

    /**
    * 教师评语
     */
    private String markingResultTeacherComment;

    /**
    * 状态
     */
    private Integer markingResultStatus;

    /**
    * 异步任务标识
     */
    private String markingResultTaskId;

    /**
    * 创建时间
     */
    private LocalDateTime markingResultCreateTime;

    /**
    * 更新时间
     */
    private LocalDateTime markingResultUpdateTime;
}