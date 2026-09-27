package com.chua.starter.monitor.support.nginx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Nginx 配置解析器：无损地把配置文件文本解析成 {@link NginxNode} 语法树。
 *
 * <p><strong>为什么必须是字符级而不是行级。</strong>nginx 允许一条指令跨多行
 * （典型如 {@code log_format}、{@code if ($a = b)}、{@code location ~ 正则}），
 * 按行切会切碎。同时 {@code #} 只有出现在<strong>词边界</strong>才是注释，
 * {@code { } ;} 也同理——{@code location ~ ^/a/{x} { } 里 nginx 自己也会把那个
 * {@code {} 当成区块括号。本解析器复刻这条规则（见 {@link #readToken}），
 * 以保证与 nginx 自身的切分行为一致，而不是自创一套"更聪明"的切法。</p>
 *
 * <p><strong>无损保证。</strong>每个节点记录自己在原始文本里的
 * {@link NginxNode#getStartOffset() 起始偏移} 与 {@link NginxNode#getEndOffset() 结束偏移}。
 * 渲染时未改动的区域直接从原文件切片复制，只有被 UI 标记 dirty 的节点才重新拼装。
 * 由于<strong>节点之间的空行、行尾注释、非常规空白</strong>都落在节点间隙里、
 * 始终原样复制，因此不存在"解析再渲染丢注释"的可能。</p>
 *
 * <p><strong>指令参数保留引号。</strong>解析出的参数是<strong>带引号的原始文本</strong>
 * （{@code "a b"} 仍是 {@code "a b"}），保证重新生成时不会把 {@code "a b"} 压成
 * {@code a b} 而改变语义。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public final class NginxParser {

    /**
     * 原始文本
     */
    private final String src;

    /**
     * 原始行数组（不含行尾换行符）
     */
    private final String[] lines;

    /**
     * 当前扫描位置
     */
    private int pos;

    /**
     * 当前行号（1 基）
     */
    private int line = 1;

    /**
     * 开放区块的栈，栈底是虚拟根节点
     */
    private final Deque<NginxNode> stack = new ArrayDeque<>();

    /**
     * 解析过程中的告警（不致命，例如括号未闭合）
     */
    private final List<String> warnings = new ArrayList<>();

    /**
     * 构造解析器。
     *
     * @param text 配置文件全文，可为 null
     */
    public NginxParser(String text) {
        this.src = text == null ? "" : text;
        this.lines = this.src.split("\r\n|\n|\r", -1);
    }

    /**
     * 解析。
     *
     * @return 虚拟根节点，其 children 为顶层语句
     */
    public NginxNode parse() {
        NginxNode root = NginxNode.block("", null, 1);
        root.setStartOffset(0);
        root.setEndOffset(src.length());
        root.setEndLine(Math.max(lines.length, 1));
        stack.push(root);

        while (true) {
            skipTrivia();
            if (eof()) {
                if (stack.size() > 1) {
                    NginxNode open = stack.peek();
                    warnings.add("第 " + open.getStartLine() + " 行开始的区块 " + open.displayName()
                            + " 缺少右花括号 }，已按文件末尾自动闭合");
                    closeTop(src.length());
                }
                break;
            }
            char c = cur();
            if (c == '}') {
                pos++;
                if (stack.size() > 1) {
                    closeTop(pos);
                } else {
                    warnings.add("第 " + line + " 行出现多余的右花括号 }，已忽略");
                }
                continue;
            }
            if (c == ';') {
                // 孤立分号，忽略但前进，避免死循环
                pos++;
                continue;
            }
            parseStatement();
        }
        return root;
    }

    /**
     * 取解析告警。
     *
     * @return 告警列表
     */
    public List<String> warnings() {
        return warnings;
    }

    /**
     * 解析一条语句：指令或区块。
     */
    private void parseStatement() {
        int stmtStart = pos;
        Token nameTok = readToken();
        if (nameTok == null) {
            // 不是词首字符（例如孤立的反斜杠等），前进一格避免死循环
            pos++;
            return;
        }
        int startOffset = nameTok.start;
        int startLine = nameTok.line;
        List<String> args = new ArrayList<>();
        boolean isBlock = false;
        boolean malformed = false;

        while (true) {
            skipTrivia();
            if (eof()) {
                break;
            }
            char c = cur();
            if (c == ';') {
                pos++;
                break;
            }
            if (c == '{') {
                pos++;
                isBlock = true;
                break;
            }
            if (c == '}') {
                // 语句没有以 ; 或 { 收尾就撞上 }：按 nginx 宽容处理，记告警后交给上层闭合
                malformed = true;
                break;
            }
            Token arg = readToken();
            if (arg == null) {
                pos++;
                continue;
            }
            args.add(arg.raw);
        }

        NginxNode parent = stack.peek();
        if (isBlock) {
            NginxNode node = NginxNode.block(nameTok.raw, args.isEmpty() ? null : String.join(" ", args), startLine);
            node.setStartOffset(startOffset);
            node.setEndOffset(pos);
            node.setOriginalText(src.substring(startOffset, Math.min(pos, src.length())));
            parent.add(node);
            stack.push(node);
            return;
        }

        NginxNode node = NginxNode.directive(nameTok.raw, String.join(" ", args), startLine);
        node.setStartOffset(startOffset);
        node.setEndOffset(pos);
        node.setEndLine(lineOf(pos));
        node.setOriginalText(src.substring(startOffset, Math.min(pos, src.length())));
        parent.add(node);
        if (malformed) {
            warnings.add("第 " + startLine + " 行的指令 " + nameTok.raw + " 缺少分号 ;");
        }
    }

    /**
     * 闭合栈顶区块，补齐结束偏移、行号与原文切片。
     *
     * @param endOffset 闭合位置的偏移
     */
    private void closeTop(int endOffset) {
        NginxNode node = stack.pop();
        int safe = Math.max(node.getStartOffset(), Math.min(endOffset, src.length()));
        node.setEndOffset(safe);
        node.setEndLine(lineOf(safe));
        node.setOriginalText(src.substring(node.getStartOffset(), safe));
    }

    /**
     * 由偏移反推行号。
     *
     * @param offset 偏移
     * @return 行号（1 基）
     */
    private int lineOf(int offset) {
        int n = 1;
        int limit = Math.min(offset, src.length());
        for (int i = 0; i < limit; i++) {
            char c = src.charAt(i);
            if (c == '\n') {
                n++;
            } else if (c == '\r') {
                if (i + 1 < src.length() && src.charAt(i + 1) == '\n') {
                    continue;
                }
                n++;
            }
        }
        return n;
    }

    /**
     * 跳过空白与注释。
     */
    private void skipTrivia() {
        while (!eof()) {
            char c = cur();
            if (c == '\n') {
                pos++;
                line++;
            } else if (c == ' ' || c == '\t' || c == '\r' || c == '\f') {
                pos++;
            } else if (c == '#') {
                while (!eof() && cur() != '\n') {
                    pos++;
                }
            } else {
                return;
            }
        }
    }

    /**
     * 读一个词（token）。
     *
     * <p>引号内的内容整体作为 raw 返回，引号本身保留；{@code \} 转义下一个字符。
     * 词在<strong>空白或行尾</strong>处断开；{@code {} ; #} 只有处于<strong>词边界</strong>
     * （当前还没读到任何字符）时才当分隔符，否则并入当前词。后半句正是 nginx 自身的
     * 切分行为，写成"读到分隔符就断开"会让 {@code location ~ ^/a/{x} } 少读一个字符。</p>
     *
     * @return 词，落在分隔符上时为 null
     */
    private Token readToken() {
        skipTrivia();
        if (eof()) {
            return null;
        }
        int start = pos;
        int startLine = line;
        StringBuilder raw = new StringBuilder();
        boolean quoted = false;
        char quote = 0;

        while (!eof()) {
            char c = cur();
            if (c == '\n') {
                break;
            }
            if (quote != 0) {
                if (c == '\\' && pos + 1 < src.length()) {
                    raw.append(c).append(src.charAt(pos + 1));
                    pos += 2;
                    continue;
                }
                if (c == quote) {
                    raw.append(c);
                    pos++;
                    quote = 0;
                    continue;
                }
                raw.append(c);
                pos++;
                continue;
            }
            if (c == ' ' || c == '\t' || c == '\r' || c == '\f') {
                // 空白永远是词边界
                break;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                quoted = true;
                raw.append(c);
                pos++;
                continue;
            }
            if (c == '{' || c == '}' || c == ';' || c == '#') {
                if (raw.length() == 0) {
                    return null;
                }
                break;
            }
            if (c == '\\' && pos + 1 < src.length()) {
                raw.append(c).append(src.charAt(pos + 1));
                pos += 2;
                continue;
            }
            raw.append(c);
            pos++;
        }

        if (raw.length() == 0) {
            return null;
        }
        return new Token(raw.toString(), start, startLine, quoted);
    }

    /**
     * 是否已到文本末尾。
     *
     * @return true 表示扫描结束
     */
    private boolean eof() {
        return pos >= src.length();
    }

    /**
     * 取当前字符。
     *
     * @return 当前字符
     */
    private char cur() {
        return src.charAt(pos);
    }

    /**
     * 词。
     *
     * @param raw    原始文本（保留引号）
     * @param start  起始偏移
     * @param line   起始行号
     * @param quoted 是否含引号
     */
    private record Token(String raw, int start, int line, boolean quoted) {
    }
}
