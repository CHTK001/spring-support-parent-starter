package com.chua.starter.eduai.model.vo;

import lombok.Data;

import java.util.List;

/**
 * AI 出题草稿 VO（尚未入库）。
 *
 * <p>由 AI 出题接口返回；教师可在前端逐条修订、勾选采纳，
 * 采纳时前端按普通保存接口逐条入库（来源标为「AI 生成」，走待审核队列）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Data
public class QuestionDraftVO {

    /** 题型（系统码值：SINGLE_CHOICE / MULTIPLE_CHOICE / JUDGE / FILL / SHORT_ANSWER / SUBJECTIVE / ESSAY） */
    private String questionType;

    /** 题干 */
    private String questionStem;

    /** 选项内容列表（仅选择题非空；已去掉「A.」等字母前缀） */
    private List<String> questionOptions;

    /** 答案（选择题为字母；判断题为「对/错」；其余为参考答案文字） */
    private String questionAnswer;

    /** 解析（考点 + 解题思路，可为空） */
    private String questionAnalysis;

    /** 难度 1-5 */
    private Integer questionDifficulty;

    /** 建议分值 */
    private Integer questionScore;
}
