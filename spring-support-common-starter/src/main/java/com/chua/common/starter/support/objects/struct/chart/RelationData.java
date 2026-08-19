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
    private List<Node> nodes = new ArrayList<>();

    /** 连线集合 */
    private List<Link> links = new ArrayList<>();

    /** 获取Nodes */
    public List<Node> getNodes() {
        return nodes;
    }

    /** 设置Nodes */
    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    /** 获取Links */
    public List<Link> getLinks() {
        return links;
    }

    /** 设置Links */
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

        /**
         * 创建 Node 实例
         * @param id id
         * @param String String
         */
        public Node(String id, String name) {
            this.id = id;
            this.name = name;
        }

        /** 获取Id */
        public String getId() {
            return id;
        }

        /** 设置Id */
        public void setId(String id) {
            this.id = id;
        }

        /** 获取Name */
        public String getName() {
            return name;
        }

        /** 设置Name */
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

        /**
         * 创建 Link 实例
         * @param source source
         * @param String String
         */
        public Link(String source, String target) {
            this.source = source;
            this.target = target;
        }

        /** 获取Source */
        public String getSource() {
            return source;
        }

        /** 设置Source */
        public void setSource(String source) {
            this.source = source;
        }

        /** 获取Target */
        public String getTarget() {
            return target;
        }

        /** 设置Target */
        public void setTarget(String target) {
            this.target = target;
        }
    }
}