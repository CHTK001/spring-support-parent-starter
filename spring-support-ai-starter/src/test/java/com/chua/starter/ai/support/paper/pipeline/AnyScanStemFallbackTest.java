// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.paper.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外来卷（ANY）题干补全链路的回归测试。
 *
 * <p>针对 2026-10-28 实测暴露的两个真实缺陷：</p>
 * <ol>
 *   <li>整卷文本一次送模型 → 国产网关内容审核 {@code 400 code=10013} → 18 题题干全空
 *       → 选择题因为没有选项而全部被判 0 分；</li>
 *   <li>任务总预算与单次调用超时共用一个 300 秒 → 18 题外来卷跑到 17/18 被整任务中止。</li>
 * </ol>
 *
 * <p>这里守住确定性部分：分段切题（不依赖模型）与原文兜底抽题干。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class AnyScanStemFallbackTest {

    /**
     * 实测外来卷第 1 页的 OCR 文本（含题号行、选项行、学生作答行）。
     */
    private static final String PAGE1 = """
            五年级综合素养测评卷
            (本卷共18题，每题5分，共90分·第1/2页)
            学号：20261205_班级：五年级(2)班
            姓名:周思远_
            一、单项选择题(每题5分)
            1.下列词语中，没有错别字的一项是（）
            A.完壁归赵
            B.守株待兔
            C.画龙点精
            D.掩耳盗玲
            (B)
            2.“干里冰封，万里雪飘”出自毛泽东的哪首词（）
            A.《沁园春·雪》
            B.《卜算子咏梅》
            C.《七律·长征》
            D.《忆秦娥委山关》
            (A)
            3.一个长方形的长是8厘米，宽是5厘米，它的周长是多少厘米（）
            A.13
            B.26
            C.40
            D.80
            (B)
            """;

    @Test
    @DisplayName("分段切题：按题号把整卷切成每题一段，且大节标题不混入段落")
    void splitsSegmentsByQuestionNo() {
        var segments = EduPaperScanner.splitSegmentsByQuestionNo(PAGE1);

        assertEquals(3, segments.size(), "应切出 3 道题，实际题号: " + segments.keySet());
        assertTrue(segments.containsKey("1"));
        assertTrue(segments.containsKey("2"));
        assertTrue(segments.containsKey("3"), "OCR 把「3.」识别正常时必须切出第 3 题");

        String q1 = segments.get("1");
        assertTrue(q1.contains("A.完壁归赵"), "第 1 题段落必须含全部选项: " + q1);
        assertTrue(q1.contains("D.掩耳盗玲"), "第 1 题段落必须含 D 选项: " + q1);
        assertFalse(q1.contains("二、判断题"), "大节标题不得混入题段落");
        assertFalse(q1.contains("2.“干里冰封"), "第 2 题内容不得混入第 1 题段落");
    }

    @Test
    @DisplayName("分段切题：题号必须递进，作答里的数字不被当成题号")
    void doesNotSplitOnNumbersInsideAnswers() {
        String text = """
                12.24的因数中，最大的一个是
                24
                13.人体最大的器官是
                皮肤
                15.一个正方形的边长是6厘米，它的面积是
                80
                平方厘米。
                """;
        var segments = EduPaperScanner.splitSegmentsByQuestionNo(text);

        // 12 → 13 递进成立；15 前面是 13，不递进（跳过 14），按「续行」处理
        assertTrue(segments.containsKey("12"), "应切出第 12 题");
        assertTrue(segments.containsKey("13"), "应切出第 13 题");
        assertFalse(segments.containsKey("15"), "不递进的题号不应切段，实际: " + segments.keySet());
        assertFalse(segments.containsKey("24"), "作答数字不得被当成题号");
        assertFalse(segments.containsKey("80"), "作答数字不得被当成题号");
    }

    @Test
    @DisplayName("原文兜底：模型被拦截时，选择题仍能从 OCR 原文拿到题干与全部选项")
    void stemsFromLayoutTextKeepOptions() {
        var segments = EduPaperScanner.splitSegmentsByQuestionNo(PAGE1);
        var questions = List.of(
                new EduPaperScanner.ScannedQuestion("1.", null, "B", null),
                new EduPaperScanner.ScannedQuestion("2.", null, "A", null),
                new EduPaperScanner.ScannedQuestion("3.", null, "B", null));

        var out = EduPaperScanner.fillStemsByLayoutText(questions, segments, null);

        assertEquals(3, out.size());
        for (var q : out) {
            assertTrue(q.stem() != null && !q.stem().isBlank(),
                    "题号 " + q.no() + " 的兜底题干不得为空");
            assertTrue(q.stem().contains("A."), "题号 " + q.no() + " 的兜底题干必须含选项: " + q.stem());
            assertFalse(q.stem().contains("(B)"), "学生作答行不得混进题干: " + q.stem());
        }
        assertTrue(out.get(0).stem().contains("没有错别字"), "第 1 题应含问句正文");
        assertTrue(out.get(0).stem().contains("掩耳盗玲"), "第 1 题应含 D 选项");
        // 作答保持不变，判分仍以作答字母为比对对象
        assertEquals("B", out.get(0).answer());
    }

    @Test
    @DisplayName("原文兜底：单行题（无选项无作答行）也能得到题干")
    void stemsFromSingleLineQuestion() {
        String text = """
                5.光在真空中的传播速度约为（）
                (B)
                """;
        var segments = EduPaperScanner.splitSegmentsByQuestionNo(text);
        var questions = List.of(new EduPaperScanner.ScannedQuestion("5.", null, "B", null));

        var out = EduPaperScanner.fillStemsByLayoutText(questions, segments, null);

        assertEquals(1, out.size());
        assertTrue(out.get(0).stem().contains("光在真空中的传播速度"),
                "题干应保留问句正文: " + out.get(0).stem());
        assertFalse(out.get(0).stem().contains("(B)"), "作答行不得混进题干");
    }

    @Test
    @DisplayName("原文兜底：模型已给出的题干不被覆盖")
    void keepsModelStem() {
        var segments = EduPaperScanner.splitSegmentsByQuestionNo(PAGE1);
        var questions = List.of(
                new EduPaperScanner.ScannedQuestion("1.", "模型给出的题干 A. B. C. D.", "B", null),
                new EduPaperScanner.ScannedQuestion("2.", null, "A", null));

        var out = EduPaperScanner.fillStemsByLayoutText(questions, segments, null);

        assertEquals(2, out.size());
        assertEquals("模型给出的题干 A. B. C. D.", out.get(0).stem(), "模型题干优先，不得被兜底覆盖");
        assertTrue(out.get(1).stem().contains("A."), "空题干才走兜底");
    }

    @Test
    @DisplayName("题干剥题号：模型回显的题号前缀被剥掉，题号数字不误吞题干内容")
    void stripsEchoedQuestionNoFromStem() {
        // 实测第 12 题：模型把题号一起抄回，题干变成 "12.24的因数中，最大的一个是"，
        // 判分模型把 12.24 当成小数，理解错了题目含义。
        assertEquals("24的因数中，最大的一个是",
                EduPaperScanner.stripStemNoPrefix("12.24的因数中，最大的一个是", "12."));
        // 第 10 题同样的题号回显
        assertEquals("长江是我国最长的河流。（）",
                EduPaperScanner.stripStemNoPrefix("10.长江是我国最长的河流。（）", "10."));
        // 行内标注 "[3] 题号 3." 也一并剥掉
        assertEquals("三角形的内角和是180度。（）",
                EduPaperScanner.stripStemNoPrefix("[3] 题号 3.三角形的内角和是180度。（）", "3."));
        // 「第 N 题」写法
        assertEquals("光在真空中的传播速度约为（）",
                EduPaperScanner.stripStemNoPrefix("第5题 光在真空中的传播速度约为（）", "5."));
        // 关键反例：题干本身以数字开头，但那个数字不是本题题号 → 必须原样保留
        assertEquals("3.14是圆周率", EduPaperScanner.stripStemNoPrefix("3.14是圆周率", "7."));
        // 题干本来就没有题号前缀 → 不动
        assertEquals("下列词语中，没有错别字的一项是（）",
                EduPaperScanner.stripStemNoPrefix("下列词语中，没有错别字的一项是（）", "1."));
    }

    @Test
    @DisplayName("作答归一化：判断题整段题干被收进作答时，只留学生写的对/错")
    void normalizesJudgeAnswer() {
        // 实测第 7 题：版面把整段题干都当成了作答
        String raw = "“画蛇添足\"这个成语比喻做了多余的事，反而不好。（） (对)";
        String stem = "“画蛇添足”这个成语比喻做了多余的事，反而不好。（）";
        assertEquals("对", EduPaperScanner.normalizeAnyAnswer(raw, stem));
    }

    @Test
    @DisplayName("作答归一化：填空题只留空位里填的内容")
    void normalizesFillAnswer() {
        // 实测第 11 题：作答带着整行题干，标准答案是「黄山」
        String raw = "五岳归来不看山， 归来不看岳。 黃山";
        String stem = "五岳归来不看山，______归来不看岳。";
        String got = EduPaperScanner.normalizeAnyAnswer(raw, stem);
        assertFalse(got.contains("五岳归来不看山"), "题干片段不得留在作答里: " + got);
        assertTrue(got.contains("黃山") || got.contains("黄山"), "学生填写内容必须保留: " + got);
    }

    @Test
    @DisplayName("作答归一化：选择题只留字母，题干为空时不做剥离")
    void normalizesChoiceAnswerWithoutStem() {
        assertEquals("B", EduPaperScanner.normalizeAnyAnswer("(B)", null));
        // 题干为空时不剥任何内容（剥了可能误删学生真实作答）
        assertEquals("学生写的完整答案", EduPaperScanner.normalizeAnyAnswer("学生写的完整答案", null));
    }

    @Test
    @DisplayName("作答归一化：整卷页脚「试卷结束」不得混入最后一题作答")
    void stripsPaperFooter() {
        // 实测第 18 题：作答尾部带着页脚，模型因此判为作答不完整、扣 2 分
        String raw = "因为书读得多，知识就丰富，写文章的时候就能写得又快又好。 试卷结束";
        String got = EduPaperScanner.normalizeAnyAnswer(raw, "为什么说“读书破万卷，下笔如有神”?请谈谈你的理解。");
        assertFalse(got.contains("试卷结束"), "页脚不得留在作答里: " + got);
        assertTrue(got.contains("知识就丰富"), "学生正文必须保留: " + got);
    }

    @Test
    @DisplayName("作答归一化：题干 OCR 错字（器宫/器官）也能剥离干净")
    void stripsStemWithOcrTypo() {
        // 实测第 13 题：题干「人体最大的器官是」，作答 OCR 成「人体最大的器宫是 皮肤」
        String got = EduPaperScanner.normalizeAnyAnswer(
                "人体最大的器宫是 皮肤", "人体最大的器官是");
        assertEquals("皮肤", got, "错一字也必须剥净题干，只留学生作答");
    }

    @Test
    @DisplayName("作答归一化：学生答案恰好以题干开头几字开头时不得误剥")
    void doesNotStripWhenAnswerMerelyStartsWithStem() {
        // 题干与作答无关（简答题），作答不应被剥掉
        String answer = "读书可以让人增长见识";
        String stem = "请谈谈你对读书的理解";
        assertEquals(answer, EduPaperScanner.normalizeAnyAnswer(answer, stem));
    }

    @Test
    @DisplayName("题干兜底：填空题干被 OCR 折行时，后半句必须留在题干里")
    void keepsWrappedStemLine() {
        // 实测第 11 题：版面块把同一行的「归来不看岳。」并进了作答。
        // 若把后半句当作答剥掉，题干残缺成「五岳归来不看山，」，模型给分在 1/3/4 分之间摇摆。
        String text = """
                11.五岳归来不看山，
                归来不看岳。
                黃山
                12.24的因数中，最大的一个是
                24
                """;
        var segments = EduPaperScanner.splitSegmentsByQuestionNo(text);
        assertTrue(segments.containsKey("11"), "应切出第 11 题，实际: " + segments.keySet());
        assertTrue(segments.containsKey("12"), "应切出第 12 题，实际: " + segments.keySet());

        var questions = List.of(new EduPaperScanner.ScannedQuestion("11.", null, "归来不看岳。 黃山", null));
        var out = EduPaperScanner.fillStemsByLayoutText(questions, segments, null);

        String stem = out.get(0).stem();
        assertTrue(stem != null && !stem.isBlank(), "题干不得为空");
        assertTrue(stem.contains("五岳归来不看山"), "题干前半句必须保留: " + stem);
        assertTrue(stem.contains("归来不看岳"), "题干折行后半句必须保留，否则题干残缺: " + stem);
    }

    @Test
    @DisplayName("题干兜底：模型题干已存在时折行合并不会覆盖它")
    void wrappedStemDoesNotOverrideModelStem() {
        String text = """
                11.五岳归来不看山，
                归来不看岳。
                黃山
                """;
        var segments = EduPaperScanner.splitSegmentsByQuestionNo(text);
        var questions = List.of(new EduPaperScanner.ScannedQuestion(
                "11.", "五岳归来不看山，______归来不看岳。", "黃山", null));
        // 全部题干已存在 → 没有需要兜底的题，按契约返回空列表，调用方保留原题
        var out = EduPaperScanner.fillStemsByLayoutText(questions, segments, null);
        assertTrue(out.isEmpty(), "已有题干时不应产出兜底结果，实际: " + out);
    }
}