// [P3C A20 豁免] A20/A30/A31: 测试数据为真实卷面文本与版面坐标，属用例字面量，豁免。
package com.chua.starter.ai.support.paper.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 版面/行规则切题的回归测试：行内多题、解答题算式、翻页页头。
 *
 * <p>针对 2026-11-05 外来卷实扫暴露的三个真实缺陷：</p>
 * <ol>
 *   <li>解答题作答 {@code a=1，b=-2，c=-3，} 里的「数字+标点」被当成行内题号，
 *       22 题被切成 27 题、第 22 题作答只剩「(20分)」；</li>
 *   <li>第 2 页页头（卷名 + 姓名/班级/学号）被并入上一题作答，第 18 题作答被污染；</li>
 *   <li>一行多题（{@code 1.B 2.A 3.C}）仍必须能正常切分，修复不能倒退。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.51
 */
class EduPaperScannerInlineSplitTest {

    /**
     * 只测切题逻辑，构造依赖（识别客户端/AI 服务/策略）均不参与。
     *
     * @return 可直接调用版面切题的扫描器
     */
    private static EduPaperScanner scanner() {
        return new EduPaperScanner(null, null, List.of(), null);
    }

    /**
     * 构造一个文字块（坐标归一化，纵向按入参递增）。
     *
     * @param text 文字块文本
     * @param y    纵向位置
     * @return 文字块
     */
    private static EduPaperScanner.ScanBlock block(String text, float y) {
        return new EduPaperScanner.ScanBlock(text, 0.95f, 0.05f, y, 0.9f, 0.05f);
    }

    /**
     * 通过反射调用私有的行规则切题（不扩大生产代码可见性）。
     *
     * @param text 版面全文
     * @return 切出的题目
     * @throws Exception 反射失败
     */
    @SuppressWarnings("unchecked")
    private static List<EduPaperScanner.ScannedQuestion> byLines(String text) throws Exception {
        Method method = EduPaperScanner.class.getDeclaredMethod("parseSelfByLines", String.class);
        method.setAccessible(true);
        return (List<EduPaperScanner.ScannedQuestion>) method.invoke(null, text);
    }

    @Test
    @DisplayName("版面切题：解答题作答里的算式不被当成题号")
    void answerWithEquationIsNotSplit() {
        var questions = scanner().extractByLayout(List.of(
                block("21.C", 0.10f),
                block("22.(20分)", 0.20f),
                block("解：a=1，b=-2，c=-3，顶点为(2,-3)", 0.25f)));

        assertEquals(2, questions.size(), "22 题只应切出 21/22 两道，实际: " + questions);
        assertEquals("22.", questions.get(1).no());
        assertTrue(questions.get(1).answer().contains("a=1"), "算式应完整留在作答里: "
                + questions.get(1).answer());
        assertFalse(questions.get(1).answer().contains("(20分)"),
                "题号后的分值不应被当成唯一作答: " + questions.get(1).answer());
    }

    @Test
    @DisplayName("版面切题：一行多题仍按题号切分")
    void inlineQuestionsStillSplit() {
        var questions = scanner().extractByLayout(List.of(block("1.B 2.A 3.C", 0.10f)));

        assertEquals(3, questions.size(), "一行三题应切出三道: " + questions);
        assertEquals("B", questions.get(0).answer());
        assertEquals("A", questions.get(1).answer());
        assertEquals("C", questions.get(2).answer());
    }

    @Test
    @DisplayName("版面切题：翻页页头不并入上一题作答")
    void pageHeaderIsDropped() {
        var questions = scanner().extractByLayout(List.of(
                block("17.√", 0.10f),
                block("九年级数学答题卡（第 2 页 共 2 页） 姓名：陈子涵 班级：九年级(1)班 学号：20260301", 0.20f),
                block("18.×", 0.30f)));

        assertEquals(2, questions.size());
        assertEquals("√", questions.get(0).answer(), "第 17 题作答不应混入页头: "
                + questions.get(0).answer());
        assertEquals("×", questions.get(1).answer());
    }

    @Test
    @DisplayName("行规则切题：一行多题仍按题号切分")
    void lineRuleSplitsInlineQuestions() throws Exception {
        var questions = byLines("1.B 2.A 3.C");

        assertEquals(3, questions.size(), "一行三题应切出三道: " + questions);
        assertEquals("B", questions.get(0).answer());
        assertEquals("C", questions.get(2).answer());
    }

    @Test
    @DisplayName("行规则切题：解答题作答里的算式不被当成题号")
    void lineRuleKeepsEquationAnswer() throws Exception {
        var questions = byLines("""
                22. 解：a=1，b=-2，c=-3，顶点为(2,-3)
                """);

        assertEquals(1, questions.size(), "解答题只应切出一道: " + questions);
        assertTrue(questions.get(0).answer().contains("a=1"), "算式应完整留在作答里: "
                + questions.get(0).answer());
    }

    @Test
    @DisplayName("行规则切题：翻页页头不并入上一题作答")
    void lineRuleDropsPageHeader() throws Exception {
        var questions = byLines("""
                17.√
                姓名：陈子涵 班级：九年级(1)班 学号：20260301
                18.×
                """);

        assertEquals(2, questions.size());
        assertEquals("√", questions.get(0).answer(), "第 17 题作答不应混入页头: "
                + questions.get(0).answer());
    }
}
