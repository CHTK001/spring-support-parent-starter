package com.chua.starter.collect.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 采集引擎的文本归一工具。
 *
 * <p>解决的问题：种子 URL 这类「逐行语义」的参数，值是以 JSON 字符串落库的。
 * 如果录入端把换行写成了字面量 {@code \n}（两个字符：反斜杠 + n），库里存的就是
 * 一整串没有真换行的文本 —— {@code split("\r?\n")} 切不开，1140 条 URL 会被当成
 * <b>一条</b> 超长 URL 交给抓取节点，报 {@code Illegal character in query at index ...}。
 * 实测「示例-GitHub 全部项目」就是这个形态（161992 字符、0 个真换行、1139 个字面 {@code \n}）。</p>
 *
 * <p>修在引擎侧而不是改种子 SQL：SQL 里的 {@code \\n} 对 MySQL 是正确的转义，
 * 换到 H2 并不等价，改一处会破坏另一处；而引擎对「字面换行」的归一化对两种库都成立，
 * 也顺带修好用户手工从文档里粘进 {@code \n} 的场景。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class CollectText {

    /**
     * 工具类不允许实例化。
     */
    private CollectText() {
    }

    /**
     * 按行切分文本，并把「字面换行」还原成真换行。
     *
     * <p>切分口径与 {@code raw.split("\r?\n")} 完全一致（不含行尾空串），
     * 只是切之前多一步 {@link #unescapeNewlines(String)}。</p>
     *
     * @param raw 原始文本，可为 null
     * @return 行列表；null / 空串返回空列表
     */
    public static List<String> lines(String raw) {
        String text = unescapeNewlines(raw);
        if (text.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(text.split("\r?\n")));
    }

    /**
     * 还原文本里的字面换行序列。
     *
     * <p>只在原文**没有任何真换行**时才还原：已经含真换行的文本按原样返回，
     * 避免把「本来就是多行、内容里恰好有反斜杠 n」的文本二次解读。
     * 反斜杠在合法 URL 里不出现，所以这一步对 URL 场景没有歧义。</p>
     *
     * <p>还原的序列：{@code \r\n}、{@code \n}、{@code \r}（均为字面的「反斜杠 + 字母」）。</p>
     *
     * @param raw 原始文本，可为 null
     * @return 还原后的文本；无需还原时原样返回
     */
    public static String unescapeNewlines(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) {
            return raw;
        }
        if (raw.indexOf('\\') < 0) {
            return raw;
        }
        return raw.replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n");
    }
}
