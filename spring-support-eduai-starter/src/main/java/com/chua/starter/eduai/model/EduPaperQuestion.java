package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
* 试卷题目关联实体
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName("edu_paper_question")
public class EduPaperQuestion {

    /**
    * 关联标识
    */
    @TableId(type = IdType.AUTO)
    /** paperquestionid */
    private Long paperQuestionId;

    /**
    * 试卷标识
    */
    private Long paperQuestionPaperId;

    /**
    * 题目标识
    */
    private Long paperQuestionQuestionId;

    /**
    * 题目顺序
    */
    private Integer paperQuestionOrder;

    /**
    * 题目分值
    */
    private Integer paperQuestionScore;

    /**
    * 创建时间
    */
    private LocalDateTime paperQuestionCreateTime;
}
