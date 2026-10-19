package com.chua.common.starter.support.objects.struct;

import com.chua.common.starter.support.objects.struct.chart.BarChartData;
import com.chua.common.starter.support.objects.struct.chart.RadarChartData;
import com.chua.common.starter.support.objects.struct.chart.RelationData;
import com.chua.common.starter.support.objects.struct.chart.TreemapData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认对象结构化实现
 *
 * @author CH
 * @since 4.0.0
 */
public class DefaultObjectStruct<T> implements ObjectStruct<T> {

    /**
     * 配置
    */
    private final TreeStructConfig<T> config;

    /**
     * 创建 默认对象struct 实例
     * @param config 配置
     */
    public DefaultObjectStruct(TreeStructConfig<T> config) {
        this.config = config;
    }

    @Override
    /**
     * 转为树
    */
    public List<TreeNode<T>> toTree(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        // 每个元素对应一个节点，容量按元素数预估
        Map<Object, TreeNode<T>> nodeMap = new HashMap<>(source.size() * 2);
        // 根节点数不超过元素总数，容量按元素数预估
        List<TreeNode<T>> roots = new ArrayList<>(source.size());

        for (T item : source) {
            Object id = config.getIdExtractor().apply(item);
            Object parentId = config.getParentIdExtractor().apply(item);
            TreeNode<T> node = new TreeNode<>(item, id, parentId);
            nodeMap.put(id, node);
        }

        for (T item : source) {
            Object id = config.getIdExtractor().apply(item);
            Object parentId = config.getParentIdExtractor().apply(item);
            TreeNode<T> node = nodeMap.get(id);

            if (parentId == null || !nodeMap.containsKey(parentId)) {
                roots.add(node);
            } else {
                nodeMap.get(parentId).addChild(node);
            }
        }

        return roots;
    }

    @Override
    /**
     * 转为barchart数据
    */
    public BarChartData toBarChartData(List<TreeNode<T>> tree) {
        if (tree == null) {
            return new BarChartData(List.of(), List.of());
        }

        List<String> categories = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        collectNodes(tree, categories, values);

        return new BarChartData(categories, values);
    }

    private void collectNodes(List<TreeNode<T>> tree, List<String> categories, List<Number> values) {
        for (TreeNode<T> node : tree) {
            if (node.getChildren() != null) {
                collectNodes(node.getChildren(), categories, values);
            }
            String name = config.getNameExtractor().apply(node.getData());
            Number value = config.getValueExtractor().apply(node.getData());
            categories.add(name);
            values.add(value);
        }
    }

    @Override
    /**
     * 转为radarchart数据
    */
    public RadarChartData toRadarChartData(List<TreeNode<T>> tree) {
        if (tree == null) {
            return new RadarChartData(List.of(), List.of());
        }

        List<String> indicators = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        collectNodes(tree, indicators, values);

        return new RadarChartData(indicators, values);
    }

    @Override
    /**
     * 转为relation数据
    */
    public RelationData toRelationData(List<TreeNode<T>> tree) {
        if (tree == null) {
            return new RelationData(List.of(), List.of());
        }

        List<RelationData.Node> nodes = new ArrayList<>();
        List<RelationData.Link> links = new ArrayList<>();
        buildRelation(tree, nodes, links);

        return new RelationData(nodes, links);
    }

    private void buildRelation(List<TreeNode<T>> tree, List<RelationData.Node> nodes, List<RelationData.Link> links) {
        for (TreeNode<T> node : tree) {
            if (node.getChildren() != null) {
                buildRelation(node.getChildren(), nodes, links);
            }
            String name = config.getNameExtractor().apply(node.getData());
            nodes.add(new RelationData.Node(String.valueOf(node.getId()), name));
            if (node.getChildren() != null) {
                for (TreeNode<T> child : node.getChildren()) {
                    links.add(new RelationData.Link(String.valueOf(node.getId()), String.valueOf(child.getId())));
                }
            }
        }
    }

    @Override
    /**
     * 转为treemap数据
    */
    public TreemapData toTreemapData(List<TreeNode<T>> tree) {
        if (tree == null) {
            return new TreemapData(List.of());
        }

        List<TreemapData.Block> blocks = new ArrayList<>();
        buildTreemap(tree, blocks);

        return new TreemapData(blocks);
    }

    private void buildTreemap(List<TreeNode<T>> tree, List<TreemapData.Block> blocks) {
        for (TreeNode<T> node : tree) {
            if (node.getChildren() != null) {
                buildTreemap(node.getChildren(), blocks);
            }
            String name = config.getNameExtractor().apply(node.getData());
            Number value = config.getValueExtractor().apply(node.getData());
            String path = buildPath(node, name);
            blocks.add(new TreemapData.Block(name, value, path));
        }
    }

    /**
     * 构建路径
     *
     * @param node 节点
     * @param currentName 当前名称
     * @return 构建路径的结果
     */
    private String buildPath(TreeNode<T> node, String currentName) {
        if (node.getParentId() == null) {
            return currentName;
        }

        // 父节点路径依赖父节点名称，当前节点信息不足，直接返回当前名称
        return currentName;
    }

}
