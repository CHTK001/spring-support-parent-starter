// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.nginx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Nginx 配置渲染器：把 {@link NginxNode} 语法树还原成配置文本。
 *
 * <p><strong>核心策略：偏移量拼接，而不是"整棵树重新生成"。</strong></p>
 *
 * <p>渲染时先找出<strong>最外层的 dirty 节点</strong>（自身被改、但没有脏祖先），
 * 把它们按偏移排序，然后在原始文本上做「复制 → 替换 → 复制 → 替换 → …」的拼接。
 * 落在这些区间之外的原文<strong>一个字节都不动</strong>。</p>
 *
 * <p>这带来三个生产级保证：</p>
 * <ul>
 *   <li>节点之间的空行、行尾注释、非常规缩进全部落在区间之外，必然被原样保留；</li>
 *   <li>解析器不认识的新指令（第三方模块、私有补丁）只是没有元数据的普通节点，
 *       同样落在区间之外，不会丢失；</li>
 *   <li>「改一个 location」只重生成那一个 location 的区间，同级其它 location 原样保留，
 *       不会连带重排。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class NginxRenderer {

    /**
     * 默认子级缩进单位
     */
    private static final String DEFAULT_INDENT = "    ";

    /**
     * 工具类不允许实例化。
     */
    private NginxRenderer() {
    }

    /**
     * 渲染整棵树。
     *
     * @param root  根节点
     * @param source 原始文本，用于复制未改动区间
     * @return 配置文本
     */
    public static String render(NginxNode root, String source) {
        String src = source == null ? "" : source;
        List<NginxNode> dirty = topmostDirty(root);
        if (dirty.isEmpty()) {
            return src;
        }
        dirty.sort(Comparator.comparingInt(NginxNode::getStartOffset));

        StringBuilder out = new StringBuilder(src.length() + 64);
        int cursor = 0;
        for (NginxNode node : dirty) {
            int start = clamp(node.getStartOffset(), 0, src.length());
            int end = clamp(node.getEndOffset(), start, src.length());
            if (start < cursor) {
                // 区间重叠说明有脏祖先被漏掉，保守跳过，交由上层区间覆盖
                continue;
            }
            out.append(src, cursor, start);
            out.append(ownIndent(node)).append(renderDirty(node));
            cursor = end;
        }
        out.append(src, cursor, src.length());
        return out.toString();
    }

    /**
     * 收集最外层的 dirty 节点。
     *
     * <p>「最外层」指自身 dirty 且父链上没有 dirty 节点。若一个脏节点被脏祖先覆盖，
     * 它的区间已被祖先的重新生成包含，再单独替换会造成区间重叠。</p>
     *
     * @param node 节点
     * @return 最外层脏节点列表
     */
    private static List<NginxNode> topmostDirty(NginxNode node) {
        List<NginxNode> out = new ArrayList<>();
        collectTopmost(node, false, out);
        return out;
    }

    /**
     * 递归收集最外层脏节点。
     *
     * @param node       节点
     * @param underDirty 祖先中是否已有脏节点
     * @param out        输出
     */
    private static void collectTopmost(NginxNode node, boolean underDirty, List<NginxNode> out) {
        boolean selfDirty = node.isDirty() && !node.isRemoved() && !node.getName().isEmpty();
        if (selfDirty && !underDirty) {
            out.add(node);
            return;
        }
        boolean nextUnder = underDirty || selfDirty;
        for (NginxNode child : node.children()) {
            collectTopmost(child, nextUnder, out);
        }
    }

    /**
     * 重新生成一个脏节点的文本。
     *
     * <p>区块按「头部 + 子节点 + 闭括号」重组，其中<strong>未改动的子节点直接复制原始区间</strong>；
     * 叶子指令按名值重新拼一行。</p>
     *
     * @param node 脏节点
     * @return 节点文本
     */
    private static String renderDirty(NginxNode node) {
        if (!node.isBlock()) {
            StringBuilder sb = new StringBuilder();
            sb.append(node.getName());
            String value = node.getValue();
            if (value != null && !value.isEmpty()) {
                sb.append(' ').append(value);
            }
            sb.append(';');
            return node.isSynthetic() ? "\n" + sb + "\n" : sb.toString();
        }

        String original = node.getOriginalText();
        // originalText 从 token 起切，首行不含缩进；闭括号那行保留完整缩进，
        // 因此以闭括号缩进作为本区块基准。基准与缩进单位都取自原文，
        // 意味着未改动的子节点整体平移量为 0，续行可以按基准原样还原。
        String blockIndent = ownIndent(node);
        String childIndent = blockIndent + detectUnit(original, blockIndent.length());

        StringBuilder sb = new StringBuilder();
        // 首行缩进由调用方给出，本方法不重复加，否则会出现双重缩进
        sb.append(node.getName());
        String headArg = node.getHeadArg();
        if (headArg != null && !headArg.isEmpty()) {
            sb.append(' ').append(headArg);
        }
        sb.append(" {\n");

        for (NginxNode child : node.children()) {
            if (child.isRemoved()) {
                continue;
            }
            if (child.isDirty() && !child.getName().isEmpty()) {
                if (child.isSynthetic()) {
                    // 新建节点自带换行，不能再套一层缩进 + 换行
                    sb.append(indentLines(renderDirty(child), childIndent));
                } else {
                    sb.append(childIndent).append(renderDirty(child)).append('\n');
                }
            } else {
                // 未改动的子节点：首行加缩进，续行按同一基准剥离后还原
                appendOriginal(sb, child.getOriginalText(), childIndent, childIndent.length());
                sb.append('\n');
            }
        }

        sb.append(blockIndent).append('}');
        return node.isSynthetic() ? "\n" + sb + "\n" : sb.toString();
    }

    /**
     * 取区块自身的缩进。
     *
     * <p>以闭括号所在行的前导空白为准——原文切片从 token 起切，首行没有缩进，
     * 只有闭括号那行还带着完整的绝对缩进。</p>
     *
     * @param node 区块节点
     * @return 缩进文本
     */
    private static String ownIndent(NginxNode node) {
        if (node.isSynthetic()) {
            return "";
        }
        return leadingOf(lastLine(node.getOriginalText()), "");
    }

    /**
     * 输出原文切片，剥掉公共前导缩进后按新缩进输出。 <p>原文是以绝对行首记录的，直接搬动会造成阶梯式右移。这里剥掉公共前缀再统一缩进， 节点被移动位置时缩进才不会错乱。</p>
     * @param indent 目标缩进
     * @param original 原文
     * @param out 输出缓冲
     * @param stripLen 整数
     * @return 返回值
    */
    private static void appendOriginal(StringBuilder out, String original, String indent, int stripLen) {
        if (original == null || original.isEmpty()) {
            return;
        }
        String[] parts = original.split("\n", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            String line = parts[i];
            if (line.isBlank()) {
                continue;
            }
            out.append(indent);
            int skip = i == 0 ? 0 : Math.min(stripLen, leadingLen(line));
            out.append(line.substring(skip));
        }
    }

    /**
     * 计算一行的前导空白长度。
     *
     * @param line 行文本
     * @return 前导空白长度
     */
    private static int leadingLen(String line) {
        int n = 0;
        while (n < line.length() && (line.charAt(n) == ' ' || line.charAt(n) == '\t')) {
            n++;
        }
        return n;
    }

    /**
     * 给多行文本的每一行加上缩进（首行除外，因为调用方通常已带缩进）。
     *
     * @param text   文本
     * @param indent 缩进
     * @return 加缩进后的文本
     */
    private static String indentLines(String text, String indent) {
        if (text.isEmpty() || indent.isEmpty()) {
            return text;
        }
        String[] parts = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                sb.append('\n');
                continue;
            }
            sb.append('\n').append(indent).append(parts[i]);
        }
        return sb.toString();
    }

    /**
     * 计算多行文本的公共前导空白长度。
     *
     * @param parts 行数组
     * @return 公共缩进长度，无缩进返回 0
     */
    private static int commonIndent(String[] parts) {
        int min = Integer.MAX_VALUE;
        for (String line : parts) {
            if (line.isBlank()) {
                continue;
            }
            int n = 0;
            while (n < line.length() && (line.charAt(n) == ' ' || line.charAt(n) == '\t')) {
                n++;
            }
            min = Math.min(min, n);
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    /**
     * 从原文推断子级缩进单位。
     *
     * <p>单位 = 「最浅子节点缩进」−「本区块基准缩进」。直接取首个缩进行会把子节点自身
     * 的缩进误当单位，导致每嵌套一层缩进翻倍。取后 {@code unit} 个空白字符而不是固定
     * 4 空格，是为了保留原文用 tab 或 2 空格缩进的写法。</p>
     *
     * @param original    原文
     * @param baseIndent  本区块基准缩进长度
     * @return 缩进单位文本
     */
    private static String detectUnit(String original, int baseIndent) {
        if (original == null || original.isEmpty()) {
            return DEFAULT_INDENT;
        }
        String[] parts = original.split("\n", -1);
        int minChild = Integer.MAX_VALUE;
        for (String line : parts) {
            if (line.isBlank()) {
                continue;
            }
            int n = leadingLen(line);
            if (n > baseIndent) {
                minChild = Math.min(minChild, n);
            }
        }
        if (minChild == Integer.MAX_VALUE || minChild <= baseIndent) {
            return DEFAULT_INDENT;
        }
        int unit = minChild - baseIndent;
        for (String line : parts) {
            if (line.isBlank()) {
                continue;
            }
            if (leadingLen(line) == minChild) {
                String lead = line.substring(0, minChild);
                return lead.substring(lead.length() - unit);
            }
        }
        return DEFAULT_INDENT;
    }

    /**
     * 取首行。
     *
     * @param text 文本
     * @return 首行，无内容返回空串
     */
    private static String firstLine(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int idx = text.indexOf('\n');
        return idx < 0 ? text : text.substring(0, idx);
    }

    /**
     * 取末行。
     *
     * @param text 文本
     * @return 末行，无内容返回空串
     */
    private static String lastLine(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int idx = text.lastIndexOf('\n');
        return idx < 0 ? text : text.substring(idx + 1);
    }

    /**
     * 取一行的前导空白，缺省用 fallback。
     *
     * @param line     行文本
     * @param fallback 缺省缩进
     * @return 前导空白
     */
    private static String leadingOf(String line, String fallback) {
        int n = 0;
        while (n < line.length() && (line.charAt(n) == ' ' || line.charAt(n) == '\t')) {
            n++;
        }
        return n == 0 ? fallback : line.substring(0, n);
    }

    /**
     * 把偏移夹到区间内。
     *
     * @param value 原值
     * @param min   下界
     * @param max   上界
     * @return 夹取结果
     */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}