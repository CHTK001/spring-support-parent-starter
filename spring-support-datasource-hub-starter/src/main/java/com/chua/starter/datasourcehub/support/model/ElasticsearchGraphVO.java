package com.chua.starter.datasourcehub.support.model;

import java.util.List;

/**
 * Elasticsearch 关系图
 * <p>
 * ES 没有外键，索引之间唯一真实的「关系」是<b>别名</b>（一个别名可指向多个索引，用于读写分离或时间切片），
 * 因此图里：索引与字段为节点（字段挂在索引下形成层级），别名为索引之间的边。
 * </p>
 *
 * @param nodes 节点：{@code type=cluster|index|field|alias}
 * @param edges 边：别名 → 索引
 * @author CH
 * @since 4.0.0.42
 */
public record ElasticsearchGraphVO(
        List<Node> nodes,
        List<Edge> edges
) {

    /**
     * 图节点
     *
     * @param id     节点唯一标识
     * @param label  展示名
     * @param type   节点类型：cluster/index/field/alias
     * @param parent 父节点标识，顶层为 null
     * @author CH
     * @since 4.0.0.42
     */
    public record Node(String id, String label, String type, String parent) {
    }

    /**
     * 图边
     *
     * @param source 起点（别名）
     * @param target 终点（索引）
     * @param label  边说明
     * @author CH
     * @since 4.0.0.42
     */
    public record Edge(String source, String target, String label) {
    }
}
