package com.chua.starter.eduai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
* 考试实体
*
* @since 4.0.0.42
* @author CH
 */
@Data
@TableName("edu_exam")
public class EduExam {

    /**
    * 考试标识
    */
    @TableId(type = IdType.AUTO)
    /** examid */
    private Long examId;

    /**
    * 试卷标识
    */
    private Long examPaperId;

    /**
    * 考试名称
    */
    private String examName;

    /**
    * 班级标识
    */
    private String examClassId;

    /**
    * 开始时间
    */
    private LocalDateTime examStartTime;

    /**
    * 结束时间
    */
    private LocalDateTime examEndTime;

    /**
    * 考试时长
    */
    private Integer examDuration;

    /**
    * 状态
    */
    private Integer examStatus;

    /**
    * 创建时间
    */
    private LocalDateTime examCreateTime;

    /**
    * 更新时间
    */
    private LocalDateTime examUpdateTime;

    /**
    * 创建人
    */
    private String examCreateBy;

    /**
    * 更新人
    */
    private String examUpdateBy;
}
