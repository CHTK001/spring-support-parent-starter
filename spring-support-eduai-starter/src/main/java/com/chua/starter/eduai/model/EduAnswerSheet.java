package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 答题卡实体
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@TableName("edu_answer_sheet")
public class EduAnswerSheet {

    /**
     * 答题卡ID
     */
    @TableId(type = IdType.AUTO)
    private Long answerSheetId;

    /**
     * 考试ID
     */
    private Long answerSheetExamId;

    /**
     * 学生ID
     */
    private String answerSheetStudentId;

    /**
     * 答题内容JSON
     */
    private String answerSheetContent;

    /**
     * 提交时间
     */
    private LocalDateTime answerSheetSubmitTime;

    /**
     * 状态
     */
    private Integer answerSheetStatus;

    /**
     * 创建时间
     */
    private LocalDateTime answerSheetCreateTime;

    /**
     * 更新时间
     */
    private LocalDateTime answerSheetUpdateTime;
}