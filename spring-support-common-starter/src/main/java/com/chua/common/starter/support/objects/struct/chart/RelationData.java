package com.chua.common.starter.support.objects.struct.chart;

import java.io.Serializable;
import java.util.List;

/**
 * 关系图数据
 *
 * @param nodes 节点集合
 * @param links 连线集合
 * @author CH
 * @since 4.0.0
 */
public record RelationData(
        List<Node> nodes,
        List<Link> links) implements Serializable {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 节点
     *
     * @param id   标识
     * @param name 名称
     * @author CH
     * @since 4.0.0
     */
    public record Node(
            String id,
            String name) implements Serializable {

        /** 串行版本UID */
        private static final long serialVersionUID = 1L;
    }

    /**
     * 连线
     *
     * @param source 来源
     * @param target 目标
     * @author CH
     * @since 4.0.0
     */
    public record Link(
            String source,
            String target) implements Serializable {

        /** 串行版本UID */
        private static final long serialVersionUID = 1L;
    }
}
