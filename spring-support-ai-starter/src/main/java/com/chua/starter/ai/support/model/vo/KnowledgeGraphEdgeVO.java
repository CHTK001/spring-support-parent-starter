package com.chua.starter.ai.support.model.vo;

/**
 * 关系图谱连线，字段与前端 {@code ReGraphEdge}（X6 数据契约）对齐。
 *
 * <p>组件名用 {@code from} / {@code to} 是为了和前端契约的 JSON 键一致，
 * 避免前端再做一次字段映射。</p>
 *
 * <p><b>刻意不加固（VoAudit 报 {@code NO_COMPACT_CTOR} 属预期）</b>：本 VO 直接序列化给
 * 前端 X6 渲染，{@code text} / {@code color} 允许缺省由前端按关系类型兜底着色；
 * 加 {@code requireNonNull} 会让「无标签连线」这种正常形态直接抛 NPE。</p>
 *
 * @param id    连线ID（关系 uid）
 * @param from  源节点ID（源实体 uid）
 * @param to    目标节点ID（目标实体 uid）
 * @param text  连线文本（关系类型）
 * @param color 连线颜色
 * @author CH
 * @since 4.0.0.42
 */
public record KnowledgeGraphEdgeVO(
        String id,
        String from,
        String to,
        String text,
        String color
) {
}
