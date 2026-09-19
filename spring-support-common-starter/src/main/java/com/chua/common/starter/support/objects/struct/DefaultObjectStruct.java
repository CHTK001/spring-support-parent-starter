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

    /** 配置 */
    private final TreeStructConfig<T> config;

    /**
     * 创建 默认对象struct 实例
     * @param config 配置
     */
    public DefaultObjectStruct(TreeStructConfig<T> config) {
        this.config = config;
    }

    @Override
    /** 转为树 */
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
    /** 转为barchart数据 */
    public BarChartData toBarChartData(List<TreeNode<T>> tree) {
        BarChartData data = new BarChartData();
        if (tree == null) {
            return data;
        }

        // 节点总数取决于树深度，容量不可预估，按需扩容
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
    /** 转为radarchart数据 */
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
    /** 转为relation数据 */
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
    /** 转为treemap数据 */
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

    /**
     * collect节点
     *
     * @param nodes 节点
     * @param result 结果
     */
    private void collectNodes(List<TreeNode<T>> nodes, List<TreeNode<T>> result) {
        for (TreeNode<T> node : nodes) {
            result.add(node);
            if (node.getChildren() != null) {
                collectNodes(node.getChildren(), result);
            }
        }
    }

    /**
     * 添加链接
     *
     * @param nodes 节点
     * @param data 数据
     */
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
