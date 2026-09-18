package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
* 试卷实体
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName("edu_paper")
public class EduPaper {

    /**
    * 试卷标识
    */
    @TableId(type = IdType.AUTO)
    /** paperid */
    private Long paperId;

    /**
    * 试卷名称
    */
    private String paperName;

    /**
    * 学科
    */
    private String paperSubject;

    /**
    * 学段
    */
    private String paperGradeLevel;

    /**
    * 年级
    */
    private String paperGrade;

    /**
    * 总分
    */
    private Integer paperTotalScore;

    /**
    * 考试时长
    */
    private Integer paperDuration;

    /**
    * 题目总数
    */
    private Integer paperQuestionCount;

    /**
    * 组卷配置JSON
    */
    private String paperConfig;

    /**
    * 状态
    */
    private Integer paperStatus;

    /**
    * 来源
    */
    private String paperSource;

    /**
    * 创建时间
    */
    private LocalDateTime paperCreateTime;

    /**
    * 更新时间
    */
    private LocalDateTime paperUpdateTime;

    /**
    * 创建人
    */
    private String paperCreateBy;

    /**
    * 更新人
    */
    private String paperUpdateBy;
}
