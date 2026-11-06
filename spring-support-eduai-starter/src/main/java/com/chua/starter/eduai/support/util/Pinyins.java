package com.chua.starter.eduai.support.util;

import com.chua.common.support.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 拼音归一化工具：把带声调拼音转成「无声调、小写、去分隔」的检索键。
 *
 * <p>为什么需要它：汉字库与词语库的拼音列存的是带声调写法（{@code zhòng}、{@code měi lì}），
 * 而老师在搜索框里敲的是 {@code zhong}、{@code meili}。两者是不同的字符串，
 * 直接 {@code LIKE} 一条都查不到 —— 这是本轮实测出来的缺陷。</p>
 *
 * <p>做法与本类同名口径保持一致：声调符号逐个映射回基础字母，{@code ü} 记作 {@code v}
 * （拼音输入法的通行写法，敲 {@code lv} 要能命中 {@code lǜ}），空格与其余分隔符全部去掉。</p>
 *
 * <p>纯静态工具，无状态、无依赖，可安全用于并发请求。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
public final class Pinyins {

    /**
     * 带声调字符 → 基础字母。逐字符映射，比链式 REPLACE 快且不会重复扫描。
     *
     * <p>覆盖 24 个带调元音（四声 × a/e/i/o/u/ü 里的 24 个组合）+ 3 个鼻音（ń ň ǹ）。</p>
     */
    private static final char[][] TONE_MAP = {
            {'ā', 'a'}, {'á', 'a'}, {'ǎ', 'a'}, {'à', 'a'},
            {'ē', 'e'}, {'é', 'e'}, {'ě', 'e'}, {'è', 'e'},
            {'ī', 'i'}, {'í', 'i'}, {'ǐ', 'i'}, {'ì', 'i'},
            {'ō', 'o'}, {'ó', 'o'}, {'ǒ', 'o'}, {'ò', 'o'},
            {'ū', 'u'}, {'ú', 'u'}, {'ǔ', 'u'}, {'ù', 'u'},
            {'ǖ', 'v'}, {'ǘ', 'v'}, {'ǚ', 'v'}, {'ǜ', 'v'},
            {'ü', 'v'},
            {'ń', 'n'}, {'ň', 'n'}, {'ǹ', 'n'},
    };

    private Pinyins() {
    }

    /**
     * 把一段带声调拼音归一化为无声调检索键。
     *
     * <p>示例：{@code zhòng → zhong}、{@code měi lì → meili}、{@code lǜ → lv}。</p>
     *
     * @param pinyin 带声调拼音，可为空
     * @return 归一化结果；入参为空时返回 {@code null}
     */
    public static String plain(String pinyin) {
        if (!StringUtils.hasText(pinyin)) {
            return null;
        }
        String lower = pinyin.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            // 分隔符（空格、逗号、分号、斜杠、点、制表）一律丢掉：多音列也能整段归一化
            if (c == ' ' || c == ',' || c == ';' || c == '/' || c == '.' || c == '\t' || c == '\'' || c == '·') {
                continue;
            }
            sb.append(demote(c));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 单字符去声调：命中映射表返回基础字母，否则原样返回。
     *
     * @param c 字符
     * @return 去声调后的字符
     */
    private static char demote(char c) {
        for (char[] pair : TONE_MAP) {
            if (pair[0] == c) {
                return pair[1];
            }
        }
        return c;
    }

    /**
     * 把多读音串拆成列表（去空、去重、保序）。
     *
     * <p>入参允许三种写法混用：逗号分隔（{@code xíng,háng}）、空格分隔（{@code xíng háng}）、
     * 单个读音（{@code zhòng}）。单读音返回单元素列表。</p>
     *
     * @param readings 多读音串，可为空
     * @return 读音列表，永不为 null
     */
    public static List<String> split(String readings) {
        if (!StringUtils.hasText(readings)) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : readings.split("[,，;；/|\\s]+")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * 派生「全部读音」列的值：单读音就是它自己，多读音用逗号拼接。
     *
     * <p>保存时如果客户端只填了主读音而没填多音列，本方法保证库里至少有一个读音，
     * 不会出现「主读音有值、多音列为空」这种检索时漏命中的状态。</p>
     *
     * @param primary 主读音（hanziPinyin）
     * @param all     客户端填写的多读音串（hanziPinyinAll，可空）
     * @return 归一后的多读音串；两者都为空时返回 {@code null}
     */
    public static String allReadings(String primary, String all) {
        List<String> readings = new ArrayList<>(split(all));
        if (StringUtils.hasText(primary)) {
            String p = primary.trim();
            if (!readings.contains(p)) {
                readings.add(0, p);
            }
        }
        return readings.isEmpty() ? null : String.join(",", readings);
    }

    /**
     * 派生拼音检索键：把「全部读音」整体归一化，各读音之间用逗号保留边界。
     *
     * <p>为什么要保留逗号：检索用 {@code LIKE '%háng%'} 时，{@code xing,hang} 能命中 hang，
     * 而不会因为去掉了分隔符变成 {@code xinghang} 后仍命中（后者也能命中，但会让
     * 「按读音前缀检索」这类诉求失效）。目前检索只做包含匹配，保留分隔符更直观。</p>
     *
     * @param allReadings 多读音串（{@link #allReadings(String, String)} 的输出）
     * @return 归一化检索键；入参为空时返回 {@code null}
     */
    public static String searchKey(String allReadings) {
        if (!StringUtils.hasText(allReadings)) {
            return null;
        }
        List<String> readings = split(allReadings);
        if (readings.isEmpty()) {
            return null;
        }
        List<String> plains = new ArrayList<>(readings.size());
        for (String reading : readings) {
            String p = plain(reading);
            if (p != null) {
                plains.add(p);
            }
        }
        return plains.isEmpty() ? null : String.join(",", plains);
    }
}
