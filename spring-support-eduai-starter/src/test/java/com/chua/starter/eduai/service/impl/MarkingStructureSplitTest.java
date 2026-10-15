package com.chua.starter.eduai.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「整页一题只读拆分」触发条件回归测试。
 *
 * <p>背景：旧版本把一整页识别成一题，前端靠版面拆分还原多道虚拟子题展示；
 * 虚拟行 {@code resultId} 为负、分数被清空且不可编辑。若对**已判分**的单题
 * 也做拆分，就会出现「汇总显示 AI 总分 50 / 老师总分 50，但逐题 AI 分整列空白、
 * 老师也无法逐题打分」的自相矛盾界面（实测任务 1acde797ab914eb5bdb00f9d8bbd0fd9）。</p>
 *
 * <p>本测试守住：只有「无任何成绩（AI 分与老师分都为空）」的整页一题才允许拆分。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
class MarkingStructureSplitTest {

    /** 非空版面页：满足拆分所需的版面数据前提 */
    private static final List<Map<String, Object>> PAGES = List.of(Map.of("pageNo", 1));

    /**
     * 无成绩的旧聚合行：仍按版面只读拆分展示（保留旧兜底行为）。
     */
    @Test
    @DisplayName("无成绩的整页一题：按版面只读拆分")
    void ungradedAggregate_splits() {
        Map<String, Object> only = new HashMap<>();
        only.put("resultId", 100L);
        only.put("no", "1");
        assertTrue(EduMarkingServiceImpl.shouldSplitLegacyAggregate(List.of(only), PAGES));
    }

    /**
     * 已判 AI 分：必须保留原始行，否则 AI 分被清空、逐题表整列空白。
     */
    @Test
    @DisplayName("已判 AI 分的整页一题：不拆分，保留 AI 分与逐题可改")
    void gradedByAi_doesNotSplit() {
        Map<String, Object> only = new HashMap<>();
        only.put("score", 50);
        assertFalse(EduMarkingServiceImpl.shouldSplitLegacyAggregate(List.of(only), PAGES));
    }

    /**
     * 已给老师分：同样必须保留，避免老师的评分被只读虚拟行顶掉。
     */
    @Test
    @DisplayName("已给老师分的整页一题：不拆分，保留老师分")
    void gradedByTeacher_doesNotSplit() {
        Map<String, Object> only = new HashMap<>();
        only.put("teacherScore", 50);
        assertFalse(EduMarkingServiceImpl.shouldSplitLegacyAggregate(List.of(only), PAGES));
    }

    /**
     * AI 判 0 分是真实成绩，不能被当成「无成绩」而拆分。
     */
    @Test
    @DisplayName("AI 判 0 分也是真实成绩：不拆分")
    void gradedZero_doesNotSplit() {
        Map<String, Object> only = new HashMap<>();
        only.put("score", 0);
        assertFalse(EduMarkingServiceImpl.shouldSplitLegacyAggregate(List.of(only), PAGES));
    }

    /**
     * 多题结果本就逐题，不触发特殊拆分。
     */
    @Test
    @DisplayName("多题结果：不触发拆分")
    void multipleQuestions_noSplit() {
        assertFalse(EduMarkingServiceImpl.shouldSplitLegacyAggregate(
                List.of(new HashMap<>(), new HashMap<>()), PAGES));
    }

    /**
     * 没有版面页数据时无从拆分。
     */
    @Test
    @DisplayName("无版面页：不拆分")
    void noPages_noSplit() {
        Map<String, Object> only = new HashMap<>();
        assertFalse(EduMarkingServiceImpl.shouldSplitLegacyAggregate(List.of(only), List.of()));
    }

    /**
     * 入参为空时安全返回 false，不抛异常。
     */
    @Test
    @DisplayName("入参为空：安全不拆分")
    void nullSafe_noSplit() {
        assertFalse(EduMarkingServiceImpl.shouldSplitLegacyAggregate(null, PAGES));
        assertFalse(EduMarkingServiceImpl.shouldSplitLegacyAggregate(List.of(new HashMap<>()), null));
    }
}
