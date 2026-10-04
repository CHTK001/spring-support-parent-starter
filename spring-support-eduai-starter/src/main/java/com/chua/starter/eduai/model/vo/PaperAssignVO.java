package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 班级试卷布置视图：布置记录 + 试卷摘要 + 班级名。
 *
 * <p>老师端「我布置的」与学生端「我班的试卷」用同一个视图：前者要看到布置到了哪个班，
 * 后者要看到卷名/学科/总分。合成一个对象省掉两次查询，也保证两端字段口径一致。</p>
 *
 * <p>用可变 JavaBean 而非 record：它是 MyBatis 的查询结果载体，依赖无参构造 + setter
 * 的映射契约（AGENTS §3.1.1 列明的例外）。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class PaperAssignVO {

    /**
     * 布置标识
     */
    private Long assignId;

    /**
     * 试卷标识
     */
    private Long paperId;

    /**
     * 试卷唯一编号（业务码）
     */
    private String paperNo;

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
     * 题目总数
     */
    private Integer paperQuestionCount;

    /**
     * 考试时长（分钟）
     */
    private Integer paperDuration;

    /**
     * 班级标识
     */
    private Long classId;

    /**
     * 班级名称
     */
    private String className;

    /**
     * 布置人账号
     */
    private String teacherAccount;

    /**
     * 状态：0 已撤回 / 1 已布置
     */
    private Integer assignStatus;

    /**
     * 备注
     */
    private String assignRemark;

    /**
     * 布置时间
     */
    private LocalDateTime assignTime;
}
