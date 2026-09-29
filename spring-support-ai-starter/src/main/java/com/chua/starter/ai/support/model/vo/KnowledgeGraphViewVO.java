package com.chua.starter.ai.support.model.vo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 关系图谱视图，字段与前端 {@code ReGraphData} 对齐，可直接透传给
 * {@code @repo/ui/re-graph-relation}。
 *
 * @param nodes      节点列表
 * @param edges      连线列表
 * @param entityCount 实体总数
 * @param relationCount 关系总数
 * @param placeholder 是否为占位图（true 表示该范围尚未抽取过关系，可触发一次生成）
 * @author CH
 * @since 4.0.0.42
 */
public record KnowledgeGraphViewVO(
        List<KnowledgeGraphNodeVO> nodes,
        List<KnowledgeGraphEdgeVO> edges,
        int entityCount,
        int relationCount,
        boolean placeholder
) {

    /**
     * 紧凑构造器：列表做防御性拷贝，保留 null 语义（无数据时前端应拿到空图而不是报错）。
     */
    public KnowledgeGraphViewVO {
        nodes = nodes == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(nodes));
        edges = edges == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(edges));
    }

    /**
     * 空图谱常量（占位图）。
     *
     * <p>{@code placeholder=true} 表示「这个范围还没有抽取过关系」，而不是「抽过但确实没有关系」。
     * 前端据此提示用户可以触发一次生成，而不是只显示一个空画布。</p>
     *
     * <p>方法名不能叫 {@code placeholder()}：record 已按组件名生成了同名实例访问器，
     * 静态方法与实例方法签名相同会编译失败。</p>
     *
     * @return 占位图谱
     */
    public static KnowledgeGraphViewVO empty() {
        return new KnowledgeGraphViewVO(List.of(), List.of(), 0, 0, true);
    }
}
