package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
* 题目实体
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName("edu_question")
public class EduQuestion {

    /**
    * 题目标识
    */
    @TableId(type = IdType.AUTO)
    /** questionid */
    private Long questionId;

    /**
    * 题型
    */
    private String questionType;

    /**
    * 学科
    */
    private String questionSubject;

    /**
    * 年段
    */
    private String questionGradeLevel;

    /**
    * 年级
    */
    private String questionGrade;

    /**
    * 年份
    */
    private String questionYear;

    /**
    * 难度（1-5）
    */
    private Integer questionDifficulty;

    /**
    * 知识点标识列表
    */
    private String questionKnowledgePointIds;

    /**
    * 题干
    */
    private String questionStem;

    /**
    * 题干图片URL
    */
    private String questionStemImages;

    /**
    * 选项JSON
    */
    private String questionOptions;

    /**
    * 标准答案
    */
    private String questionAnswer;

    /**
    * 解析
    */
    private String questionAnalysis;

    /**
    * 来源
    */
    private String questionSource;

    /**
    * 来源类型
    */
    private String questionSourceType;

    /**
    * 审核状态
    */
    private Integer questionAuditStatus;

    /**
    * 使用次数
    */
    private Integer questionUseCount;

    /**
    * 正确率
    */
    private BigDecimal questionCorrectRate;

    /**
    * 状态
    */
    private Integer questionStatus;

    /**
    * 创建时间
    */
    private LocalDateTime questionCreateTime;

    /**
    * 更新时间
    */
    private LocalDateTime questionUpdateTime;

    /**
    * 创建人
    */
    private String questionCreateBy;

    /**
    * 更新人
    */
    private String questionUpdateBy;
}
