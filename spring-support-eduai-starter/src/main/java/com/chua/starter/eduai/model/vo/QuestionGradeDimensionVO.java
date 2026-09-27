package com.chua.starter.eduai.model.vo;

import lombok.Data;

/**
 * 题库中真实存在的"学段 + 年级"组合维度。
 *
 * <p>用于前端级联筛选的候选项：选项来自题目落库值本身，而不是维度字典，
 * 避免下拉里出现题库中查不到的组合。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class QuestionGradeDimensionVO {

    /**
     * 学段编码（PRIMARY/JUNIOR/SENIOR/UNIVERSITY/OTHER）
     */
    private String gradeLevel;

    /**
     * 年级名称（与题目落库值一致，可直接作为筛选条件回传）
     */
    private String grade;

    /**
     * 该组合下的题目数量
     */
    private Integer questionCount;
}
