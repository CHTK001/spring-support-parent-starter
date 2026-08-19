package com.chua.common.starter.support.objects.struct.chart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 关系图数据
 *
 * @author CH
 */
public class RelationData implements Serializable {

    /** 节点集合 */
    /** Nodes */
    private List<Node> nodes = new ArrayList<>();

    /** 连线集合 */
    /** Links */
    private List<Link> links = new ArrayList<>();

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public List<Link> getLinks() {
        return links;
    }

    public void setLinks(List<Link> links) {
        this.links = links;
    }

    /**
     * 节点
     *
     */
    public static class Node implements Serializable {
        /** ID */
        private String id;
        /** 名称 */
        private String name;

        public Node(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * 连线
     *
     */
    public static class Link implements Serializable {
        /** 来源 */
        private String source;
        /** 目标 */
        private String target;

        public Link(String source, String target) {
            this.source = source;
            this.target = target;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }
    }
}