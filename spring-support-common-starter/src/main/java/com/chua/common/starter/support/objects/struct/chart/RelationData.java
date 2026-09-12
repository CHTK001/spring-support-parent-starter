package com.chua.common.starter.support.objects.struct.chart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 关系图数据
 *
 * @author CH
 * @since 4.0.0
 */
public class RelationData implements Serializable {
    private static final long serialVersionUID = 1L; // 串行版本uid

    /** 节点集合 */
    /** 节点 */
    private List<Node> nodes = new ArrayList<>();

    /** 连线集合 */
    /** 链接 */
    private List<Link> links = new ArrayList<>();

    /**
     * 获取节点。
     * @return 获取节点的结果
     */
    public List<Node> getNodes() {
        return nodes;
    }

    /**
     * 设置节点。
     * @param nodes 节点
     */
    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    /**
     * 获取链接。
     * @return 获取链接的结果
     */
    public List<Link> getLinks() {
        return links;
    }

    /**
     * 设置链接。
     * @param links 链接
     */
    public void setLinks(List<Link> links) {
        this.links = links;
    }

    /**
     * 节点
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Node implements Serializable {
        private static final long serialVersionUID = 1L; // 串行版本uid
        /** 标识 */
        private String id;
        /** 名称 */
        private String name;

        /**
         * 节点。
         * @param id 标识
         * @param name 名称
         */
        public Node(String id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
          * 获取标识。
         * @return 获取id的结果
         */
        public String getId() {
            return id;
        }

        /**
          * 设置标识。
         * @param id 标识
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * 获取名称。
         * @return 获取名称的结果
         */
        public String getName() {
            return name;
        }

        /**
         * 设置名称。
         * @param name 名称
         */
        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * 连线
     *
     * @author CH
     * @since 4.0.0
     */
    public static class Link implements Serializable {
        private static final long serialVersionUID = 1L; // 串行版本uid
        /** 来源 */
        private String source;
        /** 目标 */
        private String target;

        /**
         * 链接。
         * @param source 源
         * @param target Target
         */
        public Link(String source, String target) {
            this.source = source;
            this.target = target;
        }

        /**
         * 获取源。
         * @return 获取源的结果
         */
        public String getSource() {
            return source;
        }

        /**
         * 设置源。
         * @param source 源
         */
        public void setSource(String source) {
            this.source = source;
        }

        /**
         * 获取Target。
         * @return 获取Target的结果
         */
        public String getTarget() {
            return target;
        }

        /**
         * 设置Target。
         * @param target Target
         */
        public void setTarget(String target) {
            this.target = target;
        }
    }
}