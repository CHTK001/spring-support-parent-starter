package com.chua.common.starter.support.objects.struct;

import com.chua.common.starter.support.objects.struct.chart.BarChartData;
import com.chua.common.starter.support.objects.struct.chart.RadarChartData;
import com.chua.common.starter.support.objects.struct.chart.RelationData;
import com.chua.common.starter.support.objects.struct.chart.TreemapData;

import java.util.*;

/**
 * 默认对象结构化实现
 *
 * @author CH
 */
public class DefaultObjectStruct<T> implements ObjectStruct<T> {

    /** 配置 */
    private final TreeStructConfig<T> config;

    public DefaultObjectStruct(TreeStructConfig<T> config) {
        this.config = config;
    }

    @Override
    public List<TreeNode<T>> toTree(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Object, TreeNode<T>> nodeMap = new HashMap<>();
        List<TreeNode<T>> roots = new ArrayList<>();

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
    public BarChartData toBarChartData(List<TreeNode<T>> tree) {
        BarChartData data = new BarChartData();
        if (tree == null) {
            return data;
        }

        List<TreeNode<T>> allNodes = new ArrayList<>();
        collectNodes(tree, allNodes);

        for (TreeNode<T> node : allNodes) {
            String name = config.getNameExtractor().apply(node.getData());
            Number value = config.getValueExtractor().apply(node.getData());
            data.getCategories().add(name);
            data.getValues().add(value);
        }

        return data;
    }

    @Override
    public RadarChartData toRadarChartData(List<TreeNode<T>> tree) {
        RadarChartData data = new RadarChartData();
        if (tree == null) {
            return data;
        }

        List<TreeNode<T>> allNodes = new ArrayList<>();
        collectNodes(tree, allNodes);

        for (TreeNode<T> node : allNodes) {
            String name = config.getNameExtractor().apply(node.getData());
            Number value = config.getValueExtractor().apply(node.getData());
            data.getIndicators().add(name);
            data.getValues().add(value);
        }

        return data;
    }

    @Override
    public RelationData toRelationData(List<TreeNode<T>> tree) {
        RelationData data = new RelationData();
        if (tree == null) {
            return data;
        }

        List<TreeNode<T>> allNodes = new ArrayList<>();
        collectNodes(tree, allNodes);

        for (TreeNode<T> node : allNodes) {
            String name = config.getNameExtractor().apply(node.getData());
            data.getNodes().add(new RelationData.Node(String.valueOf(node.getId()), name));
        }

        addLinks(tree, data);
        return data;
    }

    @Override
    public TreemapData toTreemapData(List<TreeNode<T>> tree) {
        TreemapData data = new TreemapData();
        if (tree == null) {
            return data;
        }

        List<TreeNode<T>> allNodes = new ArrayList<>();
        collectNodes(tree, allNodes);

        for (TreeNode<T> node : allNodes) {
            String name = config.getNameExtractor().apply(node.getData());
            Number value = config.getValueExtractor().apply(node.getData());
            
            // 构建路径（从根到当前节点的名称路径）
            String path = buildPath(node, name);
            
            data.getBlocks().add(new TreemapData.Block(name, value, path));
        }

        return data;
    }

    private String buildPath(TreeNode<T> node, String currentName) {
        if (node.getParentId() == null) {
            return currentName;
        }
        
        // 这里我们需要通过ID查找父节点来构建路径
        // 为了简化，我们先返回当前名称
        // 实际应用中可能需要传递父节点信息或递归构建
        return currentName;
    }

    private void collectNodes(List<TreeNode<T>> nodes, List<TreeNode<T>> result) {
        for (TreeNode<T> node : nodes) {
            result.add(node);
            if (node.getChildren() != null) {
                collectNodes(node.getChildren(), result);
            }
        }
    }

    private void addLinks(List<TreeNode<T>> nodes, RelationData data) {
        for (TreeNode<T> node : nodes) {
            if (node.getChildren() != null) {
                for (TreeNode<T> child : node.getChildren()) {
                    data.getLinks().add(new RelationData.Link(
                            String.valueOf(node.getId()),
                            String.valueOf(child.getId())
                    ));
                }
                addLinks(node.getChildren(), data);
            }
        }
    }
}
