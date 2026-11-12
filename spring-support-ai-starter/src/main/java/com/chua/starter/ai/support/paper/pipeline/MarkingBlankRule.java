package com.chua.starter.ai.support.paper.pipeline;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 题面空位规则：判断学生作答是否落在题干空位内，并修复「题干尾段被切进作答」的识别缺陷。
 *
 * <p>两件事放在一起，因为它们都只依赖「题干 + 作答」两段文本，属于纯文本规则，
 * 与 OCR / 模型无关，可以单独做单元测试：</p>
 *
 * <ul>
 *   <li><b>严谨模式门禁</b>（{@link #outsideBlank(String, String)}）：作答必须写在
 *       {@code ______} / {@code （ ）} 里。学生把答案写在括号外、或把印刷题干片段一起
 *       带进作答时判定为「未落在空内」，本题不给分 —— 这正是阅卷设置里「匹配模式」
 *       选「严谨」时要求的口径；</li>
 *   <li><b>切分修复</b>（{@link #repairPrintedSplit(String, String)}）：版面切题把印刷
 *       尾段切进了作答（实测第 15 题：题干「一个正方形的边长是6厘米，它的面积是」、
 *       作答「平方厘米。24」，学生写的 24 反而落在后面的印刷文字之后）。把印刷尾段
 *       回填到题干、并在原位补一个空位，题干与作答就都恢复成正常人能读的形态。</li>
 * </ul>
 *
 * <p><b>判负只认证据</b>：三条证据（作答以空位右侧题干开头 / 以空位左侧题干结尾开头 /
 * 作答里出现「题干片段 + 句末标点 + 后续内容」）都是「印刷文字跑进作答」的直接迹象。
 * 没有证据时不判负 —— 严谨模式宁可漏判也不误杀，误杀一次学生就白丢一道题的分数。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
public final class MarkingBlankRule {

    /**
     * 题干空位标记：两个以上下划线、空括号（全角/半角，允许中间有空格）、空方括号。
     *
     * <p>选择题的「（ ）」也是空位：学生必须把选项字母写在括号里，因此同样受严谨模式约束。</p>
     */
    private static final Pattern BLANK = Pattern.compile("_{2,}|＿{2,}|[（(][\\s　]*[）)]|\\[[\\s　]*\\]");

    /**
     * 句末标点：用于识别混进作答的印刷题干尾段（「平方厘米。24」里的「。」）
     */
    private static final Pattern SENTENCE_END = Pattern.compile("[。！？!?]");

    /**
     * 题干以这些字收尾时，说明这一行在句中被切开了，后面本应还有印刷文字。
     *
     * <p>这是切分修复的护栏：题干收尾是正常标点（。？！）或实词时，后面跟的短句
     * 更可能是学生自己写的话，不做回填。</p>
     */
    private static final Pattern OPEN_TAIL =
            Pattern.compile("(是|为|有|得|成|用|叫|称|计|等于|共|约|的|了|和|与|在|把|被|，|,|：|:)$");

    /**
     * 印刷尾段的最大字数：超过这个长度的片段更像学生的整句话，不做回填
     */
    private static final int MAX_PRINTED_LENGTH = 10;

    private MarkingBlankRule() {
    }

    /**
     * 题干是否带空位（{@code ______} / {@code （ ）} / {@code [ ]}）。
     *
     * <p>严谨模式只对带空位的题生效：简答题没有空位，学生写在哪里都是「作答」，
     * 位置门禁对它没有意义。</p>
     *
     * @param stem 题干
     * @return true 表示题干里存在空位
     */
    public static boolean hasBlank(String stem) {
        return StringUtils.hasText(stem) && BLANK.matcher(stem).find();
    }

    /**
     * 作答是否明显落在空位之外（严谨模式判负依据）。
     *
     * <p>只认「印刷文字跑进作答」的三类证据，具体见类注释。传入的作答应当是
     * <b>归一化之后</b>的文本（已剥掉题干前缀）：归一化都剥不掉的题干碎片，说明它确实
     * 不是从空位里读出来的，此时判负才站得住脚。</p>
     *
     * @param stem   题干（含空位）
     * @param answer 归一化后的学生作答
     * @return true 表示有证据表明作答不在空位内
     */
    public static boolean outsideBlank(String stem, String answer) {
        if (!StringUtils.hasText(stem) || !StringUtils.hasText(answer) || !hasBlank(stem)) {
            return false;
        }
        String plainAnswer = plain(answer);
        if (plainAnswer.isEmpty()) {
            return false;
        }
        Matcher blank = BLANK.matcher(stem);
        if (!blank.find()) {
            return false;
        }
        String before = plain(stem.substring(0, blank.start()));
        String after = plain(stem.substring(blank.end()));
        // ① 作答以空位右侧题干开头：印刷尾段被切进了作答（「平方厘米。24」）
        if (after.length() >= 2) {
            String probe = after.substring(0, Math.min(4, after.length()));
            if (plainAnswer.startsWith(probe)) {
                return true;
            }
        }
        // ② 作答以空位左侧题干结尾开头：学生把答案写在了括号外，紧跟着题干
        if (before.length() >= 3) {
            String tail = before.substring(before.length() - Math.min(5, before.length()));
            if (plainAnswer.startsWith(tail)) {
                return true;
            }
        }
        // ③ 作答里出现「题干片段 + 句末标点 + 后续内容」：一段印刷文字被整段收进作答
        String plainStem = plain(stem);
        Matcher end = SENTENCE_END.matcher(answer);
        int previous = 0;
        while (end.find()) {
            String head = plain(answer.substring(previous, end.start()));
            boolean more = !plain(answer.substring(end.end())).isEmpty();
            if (more && head.length() >= 2 && plainStem.contains(head)) {
                return true;
            }
            previous = end.end();
        }
        return false;
    }

    /**
     * 修复「题干尾段被切进作答」的切分缺陷。
     *
     * <p>触发条件全部满足才动：题干没有空位、题干以「是 / 为 / 的有…」这类需要后续
     * 文字的字收尾、作答形如「短印刷片段 + 句末标点 + 余下内容」。命中时把印刷片段
     * 回填到题干、原位补一个空位，余下内容作为学生作答。</p>
     *
     * <p>例：题干「一个正方形的边长是6厘米，它的面积是」+ 作答「平方厘米。24」
     * → 题干「一个正方形的边长是6厘米，它的面积是（ ）平方厘米。」+ 作答「24」。</p>
     *
     * @param stem   原题干
     * @param answer 原作答
     * @return 修复后的题干与作答；不满足条件时原样返回
     */
    public static Repair repairPrintedSplit(String stem, String answer) {
        if (!StringUtils.hasText(stem) || !StringUtils.hasText(answer) || hasBlank(stem)) {
            return new Repair(stem, answer);
        }
        if (!OPEN_TAIL.matcher(stem.trim()).find()) {
            return new Repair(stem, answer);
        }
        String text = answer.trim();
        Matcher end = SENTENCE_END.matcher(text);
        if (!end.find()) {
            return new Repair(stem, answer);
        }
        String printed = text.substring(0, end.end()).trim();
        String rest = text.substring(end.end()).trim();
        String printedText = plain(printed);
        if (rest.isEmpty() || printedText.length() < 2 || printedText.length() > MAX_PRINTED_LENGTH) {
            return new Repair(stem, answer);
        }
        // 余下的内容必须是「作答」而不是又一段印刷文字：纯标点或空的不算
        if (plain(rest).isEmpty()) {
            return new Repair(stem, answer);
        }
        return new Repair(stem.trim() + "（ ）" + printed, rest);
    }

    /**
     * 只保留汉字、字母与数字：去空白、去标点，用于「包含 / 前缀」判定。
     *
     * <p>OCR 会在题干与作答里插入不同的空格与标点，直接 {@code contains} 永远对不上。</p>
     *
     * @param text 文本
     * @return 归一化后的文本；入参为空时返回空串
     */
    private static String plain(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[^\\p{IsHan}A-Za-z0-9]", "");
    }

    /**
     * 切分修复结果。
     *
     * @param stem   修复后的题干
     * @param answer 修复后的作答
     */
    public record Repair(String stem, String answer) {
    }
}
