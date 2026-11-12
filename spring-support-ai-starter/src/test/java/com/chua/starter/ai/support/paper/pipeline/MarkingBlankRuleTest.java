package com.chua.starter.ai.support.paper.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 空位规则回归测试（严谨模式门禁 + 题干/作答切分修复）。
 *
 * <p>用例取自 2026-10-29 实测任务 {@code 2324d2dd…} 的第 15 题：题干「一个正方形的边长是
 * 6厘米，它的面积是」，学生填 24，识别成「平方厘米。24」。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
class MarkingBlankRuleTest {

    /**
     * 实测第 15 题的题干（识别结果，尾部缺了印刷的「平方厘米。」）
     */
    private static final String Q15_STEM = "一个正方形的边长是6厘米，它的面积是";

    /**
     * 实测第 15 题的作答（印刷尾段被切进了作答）
     */
    private static final String Q15_ANSWER = "平方厘米。24";

    @Test
    @DisplayName("空位识别：下划线 / 空括号 / 空方括号都算空位")
    void detectsBlank() {
        assertTrue(MarkingBlankRule.hasBlank("______，志在千里。"));
        assertTrue(MarkingBlankRule.hasBlank("它的面积是（ ）平方厘米。"));
        assertTrue(MarkingBlankRule.hasBlank("它的面积是()平方厘米。"));
        assertTrue(MarkingBlankRule.hasBlank("请在[ ]内作答"));
        assertFalse(MarkingBlankRule.hasBlank("一个正方形的边长是6厘米，它的面积是"));
        assertFalse(MarkingBlankRule.hasBlank("请说明理由。"));
        assertFalse(MarkingBlankRule.hasBlank(null));
    }

    @Test
    @DisplayName("严谨模式：印刷尾段混进作答，判定未落在空内")
    void rejectsPrintedFragmentInsideAnswer() {
        String stem = "一个正方形的边长是6厘米，它的面积是（ ）平方厘米。";
        assertTrue(MarkingBlankRule.outsideBlank(stem, "平方厘米。24"));
        assertTrue(MarkingBlankRule.outsideBlank(stem, "平方厘米。 24"));
    }

    @Test
    @DisplayName("严谨模式：正常写在空内的作答不误杀")
    void acceptsAnswerInsideBlank() {
        String stem = "一个正方形的边长是6厘米，它的面积是（ ）平方厘米。";
        assertFalse(MarkingBlankRule.outsideBlank(stem, "36"));
        assertFalse(MarkingBlankRule.outsideBlank(stem, "36平方厘米"));
        assertFalse(MarkingBlankRule.outsideBlank("______，志在千里。（曹操《龟虽寿》）", "老骥伏枥"));
        assertFalse(MarkingBlankRule.outsideBlank("下列词语中，没有错别字的一项是（ ）", "B"));
        // 简答题没有空位：位置门禁不生效
        assertFalse(MarkingBlankRule.outsideBlank("请说明你的理由。", "因为……。所以……"));
        assertFalse(MarkingBlankRule.outsideBlank(stem, ""));
    }

    @Test
    @DisplayName("切分修复：第 15 题把印刷尾段回填题干、补出空位，作答只剩 24")
    void repairsPrintedSplit() {
        MarkingBlankRule.Repair repair = MarkingBlankRule.repairPrintedSplit(Q15_STEM, Q15_ANSWER);
        assertEquals("一个正方形的边长是6厘米，它的面积是（ ）平方厘米。", repair.stem());
        assertEquals("24", repair.answer());
        // 修复后按原位判定：24 落在空内，不被严谨模式误杀
        assertFalse(MarkingBlankRule.outsideBlank(repair.stem(), repair.answer()));
    }

    @Test
    @DisplayName("切分修复：题干已有空位、或题干不是句中收尾时不动")
    void keepsQuestionWhenRepairNotApplicable() {
        // 题干已有空位：交给 stripFillStem，不重复回填
        String withBlank = "一个正方形的边长是6厘米，它的面积是（ ）平方厘米。";
        assertEquals(withBlank, MarkingBlankRule.repairPrintedSplit(withBlank, "平方厘米。24").stem());
        // 题干以句号收尾：后面多半是学生自己写的一句话，不回填
        String closed = "请写出你最喜欢的科目。";
        MarkingBlankRule.Repair repair = MarkingBlankRule.repairPrintedSplit(closed, "语文。因为它有趣。");
        assertEquals(closed, repair.stem());
        assertEquals("语文。因为它有趣。", repair.answer());
        // 作答里没有句末标点：没有印刷片段可拆
        MarkingBlankRule.Repair plain = MarkingBlankRule.repairPrintedSplit(Q15_STEM, "24");
        assertEquals(Q15_STEM, plain.stem());
        assertEquals("24", plain.answer());
    }
}
