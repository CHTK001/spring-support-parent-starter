package com.chua.starter.eduai.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 外来卷「加入题库」的题型推断回归测试。
 *
 * <p>外来卷没有题型字段，题库里的题型完全由题干/作答形态推断。推断错了会直接影响
 * 组卷筛选（例如选择题被当成简答题），因此把实测里出现过的几种卷面形态固化成用例。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class MarkingToBankTypeTest {

    /**
     * 带 A./B./C./D. 四个选项的选择题应识别为单选。
     */
    @Test
    @DisplayName("成组选项识别为单选题")
    void testSingleChoice() {
        String stem = "下列词语中，没有错别字的一项是（ ）\nA.栩栩如生\nB.眼花缭乱\nC.一丝不苟\nD.专心致志";
        assertEquals("SINGLE_CHOICE", EduMarkingServiceImpl.guessQuestionType(stem, "A"));
    }

    /**
     * 题干出现「多选」字样时识别为多选。
     */
    @Test
    @DisplayName("带「多选」字样的成组选项识别为多选题")
    void testMultipleChoice() {
        String stem = "多选题：下列说法正确的有（ ）\nA.地球是圆的\nB.月球没有大气\nC.太阳是恒星";
        assertEquals("MULTIPLE_CHOICE", EduMarkingServiceImpl.guessQuestionType(stem, "ABC"));
    }

    /**
     * 选项字母在行首之外出现时不应误判为选择题（避免把题干里的「A.」引用当选项）。
     */
    @Test
    @DisplayName("只有单个选项字母不判为选择题")
    void testSingleOptionIsNotChoice() {
        String stem = "阅读下面材料，回答 A. 处应填写的词语，并说明理由。";
        assertEquals("SHORT_ANSWER", EduMarkingServiceImpl.guessQuestionType(stem, "生动"));
    }

    /**
     * 作答是对错符号时识别为判断题。
     */
    @Test
    @DisplayName("判定符号作答识别为判断题")
    void testJudge() {
        assertEquals("JUDGE", EduMarkingServiceImpl.guessQuestionType("地球是太阳系中最大的行星。", "×"));
        assertEquals("JUDGE", EduMarkingServiceImpl.guessQuestionType("水在标准大气压下100℃沸腾。", "对"));
    }

    /**
     * 仅题干含「判断」字样也识别为判断题（作答可能识别成文字）。
     */
    @Test
    @DisplayName("判断题干带填空横线时优先判为填空题")
    void testFill() {
        String stem = "五岳归来不看山，__________归来不看岳。";
        assertEquals("FILL", EduMarkingServiceImpl.guessQuestionType(stem, "黄山"));
    }

    /**
     * 既无选项也无横线的自由作答按简答题处理。
     */
    @Test
    @DisplayName("自由作答按简答题处理")
    void testShortAnswer() {
        String stem = "请结合短文内容，说说作者为什么怀念故乡的春天。";
        assertEquals("SHORT_ANSWER", EduMarkingServiceImpl.guessQuestionType(stem, "因为春天有童年的回忆"));
    }

    /**
     * 题干为空时不应抛异常（入库前已拦空题干，这里保证推断本身稳健）。
     */
    @Test
    @DisplayName("空题干不抛异常")
    void testBlankStem() {
        assertEquals("SHORT_ANSWER", EduMarkingServiceImpl.guessQuestionType(null, null));
        assertEquals("SHORT_ANSWER", EduMarkingServiceImpl.guessQuestionType("  ", ""));
    }

    /**
     * OCR 有时把选项挤成一行（整题一行、选项间空格分隔），例如
     * {@code 水的沸点在标准大气压下是多少摄氏度（） A.90 B.95 C.100 D.110}。
     * 这种形态也要能把选项单独抽出来存 {@code question_options}（JSON 数组），
     * 并且题干里不再残留选项文本。
     */
    @Test
    @DisplayName("同行空格分隔的选项也能抽成 JSON 数组")
    void testInlineOptionsExtracted() {
        String stem = "水的沸点在标准大气压下是多少摄氏度（） A.90 B.95 C.100 D.110";
        String options = EduMarkingServiceImpl.toJsonOptions(stem);
        assertEquals("[\"A.90\",\"B.95\",\"C.100\",\"D.110\"]", options);
        assertEquals("水的沸点在标准大气压下是多少摄氏度（）",
                EduMarkingServiceImpl.stripOptionsFromStem(stem));
    }

    /**
     * 行内选项里若夹着非选项文本（如「选出修词 A.…」）不应误拆，仍正常成组。
     */
    @Test
    @DisplayName("行内选项前空白/分号分隔才认选项")
    void testInlineOptionsWithoutTrailingSpace() {
        String stem = "下列词语中，没有错别字的一项是（）\nA.完壁归赵\nB.守株待兔\nC.画龙点精";
        String options = EduMarkingServiceImpl.toJsonOptions(stem);
        assertEquals("[\"A.完壁归赵\",\"B.守株待兔\",\"C.画龙点精\"]", options);
        assertEquals("下列词语中，没有错别字的一项是（）",
                EduMarkingServiceImpl.stripOptionsFromStem(stem));
    }
}
