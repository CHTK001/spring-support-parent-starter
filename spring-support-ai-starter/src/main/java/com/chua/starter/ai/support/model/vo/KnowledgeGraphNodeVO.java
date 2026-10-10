package com.chua.starter.ai.support.model.vo;

/**
 * 关系图谱节点，字段与前端 {@code ReGraphNode}（X6 数据契约）对齐。
 *
 * <p>{@code degree} 是该实体在当前图谱里的关系条数（含出边与入边），前端据此按比例
 * 缩放节点尺寸并做上下限截断；把「关系数」而不是「像素尺寸」交给前端，是为了让
 * 缩放区间可以在展示层随时调整而不需要改后端。</p>
 *
 * <p><b>刻意不加固（VoAudit 报 {@code NO_COMPACT_CTOR} 属预期）</b>：本 VO 直接序列化给
 * 前端 X6 渲染，{@code text} / {@code type} / {@code color} 允许缺省——前端
 * {@code decorateGraphNode} 对未知 {@code type} 已回退到 {@code other} 并配默认图标，
 * 不需要后端强制非空；{@code degree} 是基本类型，天然不可为 null。</p>
 *
 * @param id     节点ID（实体 uid）
 * @param text   节点显示文本（实体名称）
 * @param type   节点类型（person / location / event / organization / other）
 * @param color  节点颜色，按类型着色
 * @param weight 权重（被提及次数）
 * @param degree 关系条数，用于前端按关系多少缩放节点大小
 * @param source 来源（manual / llm_extracted / imported）
 * @param image  实体图片地址（logo 列），可为 null；前端有图时节点直接用图渲染
 * @author CH
 * @since 4.0.0.42
 */
public record KnowledgeGraphNodeVO(
        String id,
        String text,
        String type,
        String color,
        Double weight,
        int degree,
        String source,
        String image
) {
}
