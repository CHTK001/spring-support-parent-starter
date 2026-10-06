package com.chua.starter.datasourcehub.support.nginx;

import java.util.ArrayList;
import java.util.List;

/**
 * Nginx 配置语法树节点（区块或指令，二合一）。
 *
 * <p>设计目标是<strong>无损</strong>：每个节点都记住自己在原始文本里的行号区间
 * （{@link #startLine} / {@link #endLine}）与原文切片 {@link #originalText}。
 * 渲染时未被改动的区块直接吐原文，只有 {@link #dirty} 为真的区块才按指令重新拼装，
 * 因此任何未被用户碰过的配置字节都不会因为重新生成而改变。</p>
 *
 * <p>指令与区块的区分：{@link #block} 为 true 时本节点是区块（{@link #children} 有意义），
 * 为 false 时是叶子指令（{@link #value} 有意义）。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class NginxNode {

    /**
     * 指令名或区块名（location / server / http ...）
     */
    private String name = "";

    /**
     * 区块头部参数，例如 {@code location /api { } 的 "/api"}、{@code upstream backend { }} 的 "backend"
     */
    private String headArg = null;

    /**
     * 是否为区块
     */
    private boolean block;

    /**
     * 叶子指令的原始参数文本（已按 nginx 语义去引号，保留内部转义）
     */
    private String value = "";

    /**
     * 子节点
     */
    private final List<NginxNode> children = new ArrayList<>();

    /**
     * 起始行号（1 基，含本行）
     */
    private int startLine = 1;

    /**
     * 结束行号（1 基，含本行）
     */
    private int endLine = 1;

    /**
     * 在原始文本中的起始偏移（含）
     */
    private int startOffset;

    /**
     * 在原始文本中的结束偏移（不含）
     */
    private int endOffset;

    /**
     * 原始文本切片，未改动时直接复用
     */
    private String originalText = "";

    /**
     * 是否被 UI 改动过
     */
    private boolean dirty;

    /**
     * 是否已被删除（UI 模式移除节点）
     */
    private boolean removed;

    /**
     * 是否为 UI 新建的节点（原始文本中不存在，需要生成插入区间）
     */
    private boolean synthetic;

    /**
     * 构造一个区块节点。
     *
     * @param name     区块名
     * @param headArg  区块头部参数，可为 null
     * @param startLine 起始行号
     * @return 区块节点
     */
    public static NginxNode block(String name, String headArg, int startLine) {
        NginxNode node = new NginxNode();
        node.name = name == null ? "" : name;
        node.headArg = headArg;
        node.block = true;
        node.startLine = startLine;
        return node;
    }

    /**
     * 构造一个叶子指令节点。
     *
     * @param name      指令名
     * @param value     参数文本
     * @param startLine 起始行号
     * @return 指令节点
     */
    public static NginxNode directive(String name, String value, int startLine) {
        NginxNode node = new NginxNode();
        node.name = name == null ? "" : name;
        node.value = value == null ? "" : value;
        node.startLine = startLine;
        return node;
    }

    /**
     * 取子节点。
     *
     * @return 子节点列表
     */
    public List<NginxNode> children() {
        return children;
    }

    /**
     * 追加子节点。
     *
     * @param child 子节点
     * @return 本节点，便于链式调用
     */
    public NginxNode add(NginxNode child) {
        children.add(child);
        return this;
    }

    /**
     * 区块的显示标题：区块名 + 头部参数。
     *
     * @return 标题文本
     */
    public String displayName() {
        if (headArg == null || headArg.isEmpty()) {
            return name;
        }
        return name + " " + headArg;
    }

    /**
     * 取区块名。
     *
     * @return 区块名
     */
    public String getName() {
        return name;
    }

    /**
     * 设置区块名。
     *
     * @param name 区块名
     */
    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    /**
     * 取区块头部参数。
     *
     * @return 头部参数，未设置时为 null
     */
    public String getHeadArg() {
        return headArg;
    }

    /**
     * 设置区块头部参数。
     *
     * @param headArg 头部参数
     */
    public void setHeadArg(String headArg) {
        this.headArg = headArg;
    }

    /**
     * 是否为区块。
     *
     * @return true 表示区块
     */
    public boolean isBlock() {
        return block;
    }

    /**
     * 标记为区块。
     *
     * @param block 是否区块
     */
    public void setBlock(boolean block) {
        this.block = block;
    }

    /**
     * 取指令参数文本。
     *
     * @return 参数文本
     */
    public String getValue() {
        return value;
    }

    /**
     * 设置指令参数文本。
     *
     * @param value 参数文本
     */
    public void setValue(String value) {
        this.value = value == null ? "" : value;
    }

    /**
     * 取起始行号。
     *
     * @return 起始行号
     */
    public int getStartLine() {
        return startLine;
    }

    /**
     * 设置起始行号。
     *
     * @param startLine 起始行号
     */
    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    /**
     * 取结束行号。
     *
     * @return 结束行号
     */
    public int getEndLine() {
        return endLine;
    }

    /**
     * 设置结束行号。
     *
     * @param endLine 结束行号
     */
    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    /**
     * 取原始文本切片。
     *
     * @return 原始文本
     */
    public String getOriginalText() {
        return originalText;
    }

    /**
     * 设置原始文本切片。
     *
     * @param originalText 原始文本
     */
    public void setOriginalText(String originalText) {
        this.originalText = originalText == null ? "" : originalText;
    }

    /**
     * 取原始起始偏移。
     *
     * @return 起始偏移
     */
    public int getStartOffset() {
        return startOffset;
    }

    /**
     * 设置原始起始偏移。
     *
     * @param startOffset 起始偏移
     */
    public void setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }

    /**
     * 取原始结束偏移。
     *
     * @return 结束偏移
     */
    public int getEndOffset() {
        return endOffset;
    }

    /**
     * 设置原始结束偏移。
     *
     * @param endOffset 结束偏移
     */
    public void setEndOffset(int endOffset) {
        this.endOffset = endOffset;
    }

    /**
     * 是否被改动。
     *
     * @return true 表示被 UI 改过
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * 标记改动状态。
     *
     * @param dirty 是否改动
     */
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    /**
     * 是否已删除。
     *
     * @return true 表示已删除
     */
    public boolean isRemoved() {
        return removed;
    }

    /**
     * 标记删除状态。
     *
     * @param removed 是否删除
     */
    public void setRemoved(boolean removed) {
        this.removed = removed;
    }

    /**
     * 是否为 UI 新建节点。
     *
     * @return true 表示原始文本中不存在该节点
     */
    public boolean isSynthetic() {
        return synthetic;
    }

    /**
     * 标记为 UI 新建节点，并给定插入点。
     *
     * @param synthetic       是否新建
     * @param insertionOffset 插入偏移（零宽区间）
     */
    public void markSynthetic(boolean synthetic, int insertionOffset) {
        this.synthetic = synthetic;
        this.startOffset = insertionOffset;
        this.endOffset = insertionOffset;
    }
}
